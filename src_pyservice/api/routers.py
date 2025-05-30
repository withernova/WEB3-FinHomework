import logging
from flask import Blueprint, request, jsonify, send_file
from services.tag_extractor import TagExtractor
from services.volunteer_recommender import VolunteerRecommender
from config import Config
from services.recommendation_service import RecommendationService
from utils.docx_generator import generate_docx_from_markdown
import os
import uuid
import tempfile

api_bp = Blueprint("api", __name__)
logger = logging.getLogger(__name__)

tag_extractor = TagExtractor()
recommendation_service = RecommendationService()

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


@api_bp.route("/recommend-rescuers", methods=["POST"])
def recommend_rescuers():
    try:
        logger.info("收到/recommend-rescuers请求")
        
        data = request.json
        logger.debug(f"请求体: {data!r}")
        if not data:
            logger.warning("请求数据为空")
            return jsonify({"success": False, "message": "请求数据为空"}), 400
        
        # 验证必要字段
        if "task" not in data or "rescuers" not in data:
            logger.warning(f"缺少必要字段, data.keys(): {list(data.keys())}")
            return jsonify({"success": False, "message": "缺少任务或救援人员数据"}), 400
        
        # 获取标签库
        tag_library = data.get("tagLibrary", [])
        
        logger.info("调用推荐服务: generate_recommendations")
        result = recommendation_service.generate_recommendations(
            data["task"], 
            data["rescuers"],
            tag_library
        )
        logger.debug(f"推荐服务返回: {result!r}")
        
        if not result.get("success"):
            logger.warning(f"推荐服务失败: {result}")
            return jsonify(result), 400
        
        # 生成唯一文件名
        report_id = str(uuid.uuid4())
        temp_dir = getattr(Config, "TEMP_FILE_DIR", tempfile.gettempdir())
        os.makedirs(temp_dir, exist_ok=True)
        markdown_path = os.path.join(temp_dir, f"report_{report_id}.md")
        docx_path = os.path.join(temp_dir, f"report_{report_id}.docx")
        logger.info(f"生成临时文件: {markdown_path}, {docx_path}")
        
        # 将Markdown内容写入临时文件
        md_content = result.get("report_markdown", "")
        if not md_content:
            logger.error("推荐结果未包含 report_markdown 字段")
            return jsonify({"success": False, "message": "推荐结果无报告内容"}), 500
        with open(markdown_path, "w", encoding="utf-8") as f:
            f.write(md_content)
        logger.info("写入Markdown临时文件完成")
        
        # 使用pandoc生成Word文档
        logger.info("调用generate_docx_from_markdown生成docx")
        docx_url = generate_docx_from_markdown(
            markdown_path, 
            docx_path, 
            template_path=Config.DOCX_TEMPLATE_PATH
        )
        logger.info(f"生成docx完成: {docx_url}")
        
        # 添加文档URL到结果中
        result["docx_url"] = f"/api/download-report/{report_id}"
        
        # 保存文件路径供下载使用
        if not hasattr(Config, "TEMP_FILES"):
            Config.TEMP_FILES = {}
        Config.TEMP_FILES[report_id] = docx_path
        logger.debug(f"已保存docx路径: {Config.TEMP_FILES[report_id]}")
        
        # 返回推荐结果
        logger.info("推荐结果处理完成, 正常返回")
        return jsonify(result), 200
    
    except Exception as e:
        logger.error(f"推荐救援人员时发生错误: {str(e)}", exc_info=True)
        return jsonify({"success": False, "message": f"服务器错误: {str(e)}"}), 500

@api_bp.route("/download-report/<report_id>", methods=["GET"])
def download_report(report_id):
    try:
        if not hasattr(Config, "TEMP_FILES") or report_id not in Config.TEMP_FILES:
            return jsonify({"success": False, "message": "报告不存在或已过期"}), 404
        
        docx_path = Config.TEMP_FILES[report_id]
        if not os.path.exists(docx_path):
            return jsonify({"success": False, "message": "报告文件已被删除"}), 404
        
        # 提供文件下载
        return send_file(
            docx_path,
            mimetype="application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            as_attachment=True,
            download_name=f"救援人员推荐报告_{report_id}.docx"
        )
    
    except Exception as e:
        logger.error(f"下载报告时发生错误: {str(e)}", exc_info=True)
        return jsonify({"success": False, "message": f"服务器错误: {str(e)}"}), 500

# 添加一个定期清理临时文件的路由(仅用于开发环境)
@api_bp.route("/cleanup-temp-files", methods=["POST"])
def cleanup_temp_files():
    if not Config.DEBUG:
        return jsonify({"success": False, "message": "此功能仅在开发环境可用"}), 403
    
    try:
        if hasattr(Config, "TEMP_FILES"):
            for file_path in Config.TEMP_FILES.values():
                if os.path.exists(file_path):
                    os.remove(file_path)
            Config.TEMP_FILES = {}
        
        return jsonify({"success": True, "message": "临时文件已清理"}), 200
    
    except Exception as e:
        logger.error(f"清理临时文件时发生错误: {str(e)}", exc_info=True)
        return jsonify({"success": False, "message": f"服务器错误: {str(e)}"}), 500
    

@api_bp.route("/generate-summary", methods=["POST"])
def generate_elder_summary():
    print("114514")
    try:
        logger.info("收到/generate-summary请求")
        
        data = request.json
        if not data:
            logger.warning("请求数据为空")
            return jsonify({"success": False, "message": "请求数据为空"}), 400
        
        logger.debug(f"模板数据字段: {list(data.keys())}")
        
        # 调用服务生成摘要
        summary = recommendation_service.generate_elder_summary(data)
        
        logger.info(f"生成摘要完成，长度: {len(summary)}")
        
        return jsonify({
            "success": True,
            "summary": summary
        }), 200
    
    except Exception as e:
        logger.error(f"生成摘要时发生错误: {str(e)}", exc_info=True)
        return jsonify({"success": False, "message": f"服务器错误: {str(e)}"}), 500
    

