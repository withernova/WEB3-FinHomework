from flask import Blueprint, request, jsonify, current_app
from services.poster_generator import PosterGenerator
import logging

poster_bp = Blueprint('poster', __name__)
logger = logging.getLogger(__name__)

# 创建服务实例
poster_generator = PosterGenerator()


@poster_bp.route('/ping', methods=['GET'])
def ping():
    """健康检查端点"""
    return jsonify({"message": "pong"}), 200

@poster_bp.route('/generate-html-poster', methods=['POST'])
def generate_html_poster():
    """生成HTML海报端点"""
    try:
        data = request.json
        logger.info(f"收到海报生成请求: {data.get('elderName')}")
        
        # 基本参数验证
        required_fields = ['elderName', 'lostTime', 'location', 'photoUrl']
        for field in required_fields:
            if not data.get(field):
                return jsonify({
                    'success': False,
                    'message': f'缺少必要参数: {field}'
                }), 400
        
        # 调用海报生成器
        result = poster_generator.generate(data)
        return jsonify(result)
        
    except Exception as e:
        logger.exception("生成海报时发生错误")
        return jsonify({
            'success': False,
            'message': f'服务器错误: {str(e)}'
        }), 500