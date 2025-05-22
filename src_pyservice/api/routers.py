import logging
from flask import Blueprint, request, jsonify
from services.tag_extractor import TagExtractor
from services.volunteer_recommender import VolunteerRecommender
from config import Config

api_bp = Blueprint("api", __name__)
logger = logging.getLogger(__name__)

tag_extractor = TagExtractor()
recommender   = VolunteerRecommender()

# ----------------------------------------------------------------------
# 0. ping — 用于连通性测试
# ----------------------------------------------------------------------
@api_bp.route("/ping", methods=["GET"])
def ping():
    return {"msg": "pong"}, 200


# ----------------------------------------------------------------------
# 1. 提取标签接口（保留，未改动）
# ----------------------------------------------------------------------
@api_bp.route("/extract_tags", methods=["POST"])
def extract_tags():
    try:
        data = request.get_json(silent=True) or {}
        if "user_bio" not in data:
            return jsonify({"error":"user_bio 不能为空"}), 400

        tags = tag_extractor.extract_tags(
            data["user_bio"].strip(),
            data.get("tag_library")
        )
        return jsonify({"tags": tags})

    except Exception as e:
        logger.exception("extract_tags error")
        return jsonify({"error":"服务器内部错误"}), 500


# ----------------------------------------------------------------------
# 2. 推荐志愿者接口（带 DEBUG 日志）
# ----------------------------------------------------------------------
@api_bp.route("/recommend_volunteers", methods=["POST"])
def recommend_volunteers():
    try:
        data = request.get_json(silent=True) or {}
        logger.info(f"收到推荐请求: {data}")  # 若看不到，说明请求没到

        family      = data.get("family_name",  "尊敬的家属")
        lost_addr   = data.get("lost_address", "未知地点")
        needed_tags = data.get("needed_tags") or []

        report = recommender.gen_report(
            family_name  = family,
            lost_address = lost_addr,
            tags         = needed_tags
        )
        logger.debug(f"生成的报告前100字: {report[:100]}")
        return jsonify({"report": report})

    except Exception:
        logger.exception("recommend_volunteers error")
        return jsonify({"error":"服务器内部错误"}), 500
