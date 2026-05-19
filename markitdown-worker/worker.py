import pika
import json
import requests
import tempfile
import os
from markitdown import MarkItDown

# 初始化微软解析神器
md_converter = MarkItDown()


def download_file(url: str, suffix: str) -> str:
    """从 OSS/URL 下载文件到本地临时目录"""
    response = requests.get(url)
    response.raise_for_status()  # 如果下载失败（比如404），直接抛出异常

    # 创建安全的临时文件
    fd, temp_path = tempfile.mkstemp(suffix=suffix)
    with os.fdopen(fd, 'wb') as f:
        f.write(response.content)
    return temp_path


def callback(ch, method, properties, body):
    print(f"\n[*] 收到新任务...")
    temp_path = None
    try:
        # 1. 跨语言解包：把 Java 发来的 JSON 字节流转成 Python 字典
        msg = json.loads(body.decode('utf-8'))
        original_file_name = msg.get('fileName', 'unknown_file')
        oss_url = msg.get('ossUrl')
        file_type = msg.get('fileType', '.docx')

        print(f"    - 正在下载文件: {oss_url}")

        # 2. 下载文件
        temp_path = download_file(oss_url, file_type)

        # 3. 召唤 MarkItDown 进行降维打击
        print(f"    - 正在使用 MarkItDown 解析...")
        result = md_converter.convert(temp_path)
        markdown_text = result.text_content

        # 4. 组装发回给 Java 的结果（原样带回业务 ID）
        result_msg = {
            "ossUrl": oss_url,
            "fileName": original_file_name,
            "attractionId": msg.get("attractionId"),
            "adminId": msg.get("adminId"),
            "taskId": msg.get("taskId"),
            "fileType": file_type,
            "markdownText": markdown_text,
            "success": True
        }

        # 5. 发送到结果队列
        ch.basic_publish(
            exchange='',
            routing_key='doc.convert.result',
            body=json.dumps(result_msg, ensure_ascii=False),
            properties=pika.BasicProperties(
                content_type='application/json',
                delivery_mode=2  # 消息持久化
            )
        )
        print(f"[√] 任务完成！Markdown 文本长度: {len(markdown_text)}")

    except Exception as e:
        print(f"[X] 任务执行失败: {e}")
        # 如果需要，这里也可以发一条 success: False 的消息回 MQ 通知 Java
    finally:
        # 6. 核心素养：无论成功还是报错，必须删掉临时文件，绝不弄脏服务器硬盘！
        if temp_path and os.path.exists(temp_path):
            os.remove(temp_path)

    # 7. 手动 ACK：告诉 MQ 任务做完了，可以把这条消息删了
    ch.basic_ack(delivery_tag=method.delivery_tag)


def main():
    # 建立与 RabbitMQ 的连接
    # 注意：这里的账号密码和端口，对应你 docker-compose.yml 里的配置
    credentials = pika.PlainCredentials(
        username=os.getenv('RABBITMQ_USER', 'admin'),
        password=os.getenv('RABBITMQ_PASS', 'admin')
    )
    parameters = pika.ConnectionParameters(
        host=os.getenv('RABBITMQ_HOST', 'localhost'),
        port=5672,
        virtual_host='/',
        credentials=credentials,
        connection_attempts=5,  # 加上重试
        retry_delay=3
    )
    connection = pika.BlockingConnection(parameters)
    channel = connection.channel()

    # 声明双向队列（如果 RabbitMQ 里没有这两个队列，会自动创建）
    channel.queue_declare(queue='doc.convert.result', durable=True)

    # QoS配置：每次只从队列里拿 1 个任务，做完再拿下一个，防止内存溢出
    channel.basic_qos(prefetch_count=1)

    # 监听请求队列
    channel.basic_consume(queue='doc.convert.request', on_message_callback=callback)

    print(' [*] Python Worker 已就绪！坐等 Java 老哥发包...')
    channel.start_consuming()


if __name__ == '__main__':
    main()