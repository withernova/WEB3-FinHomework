from flask import Blueprint, request, jsonify, render_template
import uuid

from services.psych_care_service import PsychCareService

psych_care_bp = Blueprint("psych_care_bp", __name__)
svc = PsychCareService()


@psych_care_bp.route("/psych-care/chat", methods=["POST"])
def psych_chat():
    """
    POST /api/psych-care/chat
    请求体: { "message": "...", "session_id": "可选" }
    返回:   { "session_id": "...", "reply": "..." }
    """
    data = request.get_json(force=True) or {}
    message = (data.get("message") or "").strip()
    if not message:
        return jsonify(error="message不能为空"), 400

    session_id = data.get("session_id") or str(uuid.uuid4())
    reply = svc.chat(session_id, message)
    return jsonify(session_id=session_id, reply=reply)


@psych_care_bp.route("/psych-care", methods=["GET"])
def psych_page():
    """前端页面（纯静态 HTML/JS），也可独立放到 SpringBoot"""
    return render_template("psych_care_chat.html")
