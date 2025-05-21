from flask import Blueprint, request, jsonify
from services.tag_extractor import TagExtractor
from config import Config
import logging

# 创建蓝图
api_bp = Blueprint('api', __name__)
logger = logging.getLogger(__name__)

# 创建单例标签提取器
tag_extractor = TagExtractor()

@api_bp.route("/extract_tags", methods=["POST"])
def extract_tags():
    """提取标签API端点
    
    请求体例子:
    {
        "user_bio": "我擅长游泳，登山20年经验，参加过多次高海拔救援",
        "user_id": 123,  # 可选字段
        "tag_library": ["标签1", "标签2", ...]  # 可选字段，如果提供将使用这个标签库
    }
    
    响应例子:
    {
        "tags": ["水上救援", "攀登技能", "高海拔耐受"]
    }
    """
    try:
        # 获取并验证请求数据
        data = request.get_json(silent=True)
        if not data:
            logger.warning("收到无效的JSON数据")
            return jsonify({"error": "请求必须是有效的JSON"}), 400
            
        if "user_bio" not in data:
            logger.warning("请求缺少user_bio字段")
            return jsonify({"error": "请求体必须包含user_bio字段"}), 400
            
        user_bio = str(data.get("user_bio", "")).strip()
        user_id = data.get("user_id")
        
        # 获取标签库 - 如果请求中提供了tag_library，则使用请求中的标签库
        tag_library = data.get("tag_library")
        #print(tag_library)
        print("我们的用户会："+user_bio)
        
        if not user_bio:
            logger.warning("user_bio为空")
            return jsonify({"error": "user_bio不能为空"}), 400
            
        # 记录请求信息
        if user_id:
            logger.info(f"开始为用户{user_id}提取标签")
        else:
            logger.info("开始提取标签（匿名用户）")
            
        # 如果提供了标签库，记录信息
        if tag_library:
            logger.info(f"使用自定义标签库，包含{len(tag_library)}个标签")
        else:
            logger.info(f"使用默认标签库，包含{len(Config.TAG_LIBRARY)}个标签")
            
        # 提取标签
        tags = tag_extractor.extract_tags(user_bio, tag_library)
        
        # 返回结果
        return jsonify({"tags": tags})
    except Exception as e:
        logger.error(f"处理请求时发生错误: {str(e)}")
        return jsonify({"error": "服务器内部错误"}), 500
@api_bp.route("/recommend_volunteers", methods=["POST"])
def recommend_volunteers():
    """
    请求体示例:
    {
      "family_name": "李先生",
      "lost_address": "杭州市西湖区灵隐路",
      "lost_lat": 30.240,         # WGS84
      "lost_lon": 120.109,
      "needed_tags": ["城市导航","搜索技能"]
    }
    """
    try:
        data = request.get_json(silent=True) or {}
        lost_lat  = float(data.get("lost_lat"))
        lost_lon  = float(data.get("lost_lon"))
        lost_addr = data.get("lost_address","未知地点")
        family    = data.get("family_name","尊敬的家属")
        tags      = data.get("needed_tags") or []

    except Exception:
        return jsonify({"error":"参数缺失或格式错误"}), 400

    report = recommender.gen_report(
        family_name=family,
        lost_address=lost_addr,
        lost_lat=lost_lat,
        lost_lon=lost_lon,
        tags=tags
    )
    return jsonify({"report": report})