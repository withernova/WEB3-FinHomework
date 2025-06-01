# app.py
import logging

from flask import Flask
from flask_cors import CORS          # pip install flask-cors

from config import Config
from utils.logging_config import setup_logging

# --- 蓝图导入 ----
from api.routers import api_bp             # 原有接口
from api.psych_care_router import psych_care_bp   # 心理关怀 GPT 接口
from api.poster_router import poster_bp
from api.map_router import map_bp


def create_app() -> Flask:
    """
    初始化并返回 Flask 应用
    """
    # 1. 日志
    setup_logging(log_file="logs/app.log")
    logger = logging.getLogger(__name__)

    # 2. 创建实例
    app = Flask(__name__)

    # 3. 全局 CORS：只对 /api/* 生效（如需限制域名，把 "*" 改成 "http://127.0.0.1:8080" 等）
    CORS(
        app,
        resources={r"/api/*": {"origins": "*"}},
        supports_credentials=True
    )

    # 4. 注册蓝图
    app.register_blueprint(api_bp, url_prefix="/api")
    app.register_blueprint(psych_care_bp, url_prefix="/api")
    app.register_blueprint(poster_bp, url_prefix='/api')
    app.register_blueprint(map_bp, url_prefix='/api')


    logger.info("Flask 应用初始化完成")
    return app


if __name__ == "__main__":
    application = create_app()
    # 监听地址与端口来自 config.py
    application.run(
        host=Config.HOST,
        port=Config.PORT,
        debug=Config.DEBUG
    )