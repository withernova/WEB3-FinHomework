from flask import Flask
from api.routers import api_bp
from utils.logging_config import setup_logging
from config import Config
import logging

def create_app() -> Flask:
    setup_logging(log_file="logs/app.log")
    logger = logging.getLogger(__name__)

    app = Flask(__name__)
    app.register_blueprint(api_bp, url_prefix="/api")  # 所有接口挂在 /api/*
    logger.info("Flask 应用初始化完成")
    return app

if __name__ == "__main__":
    app = create_app()
    app.run(host=Config.HOST, port=Config.PORT, debug=Config.DEBUG)
