from fastapi import APIRouter, HTTPException
from fastapi.responses import FileResponse

from config import TEST_VIDEO_DIR

router = APIRouter()


@router.get("/admin/test-video/{attraction_id}")
async def serve_test_video(attraction_id: str):
    video_path = TEST_VIDEO_DIR / f"{attraction_id}.mp4"
    if not video_path.exists():
        raise HTTPException(status_code=404, detail="测试视频不存在")
    return FileResponse(video_path, media_type="video/mp4")
