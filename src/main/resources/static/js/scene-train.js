layui.use(['layer', 'form'], function () {
  const layer = layui.layer;
  const form  = layui.form;

  /* 端点与会话 */
  const api = window.SCENE_API_URL;                 // = http://127.0.0.1:5000/api/scene-train
  const sid = localStorage.getItem("scene_sid") || crypto.randomUUID();
  localStorage.setItem("scene_sid", sid);

  /* DOM */
  const $msg   = $('#msgBox'),
        $input = $('#input'),
        $btn   = $('#sendBtn'),
        $score = $('#scoreCard'),
        $scoreBody = $('#scoreBody');

  let scene = "";               // 当前场景 key

  /* ========= 场景选择监听 ========= */
  form.on('select(sceneSel)', function (data) {
    scene = data.value;         // 取到选项的 value
    if (!scene) {               // 未选中
      $btn.prop('disabled', true);
      return;
    }
    // 初始化界面
    $msg.empty();
    $score.hide();
    $btn.prop('disabled', true);
    initScene();
  });

  /* ========= 初始化场景（系统首句） ========= */
  async function initScene() {
    appendMsg("系统已进入场景，请开始对话", "sys");
    const res = await post({ cmd: "start", scene });
    appendMsg(res.reply, "bot");
    $btn.prop('disabled', false);
  }

  /* ========= 发送按钮 / Enter ========= */
  $btn.on('click', send);
  $input.on('keydown', e => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      send();
    }
  });

  async function send() {
    if (!scene) { layer.msg('请选择场景', { icon: 0 }); return; }

    const txt = $input.val().trim();
    if (!txt) { layer.msg('请输入内容', { icon: 0 }); return; }

    appendMsg(txt, "user");
    $input.val("");
    $btn.prop('disabled', true);

    const data = await post({ cmd: "talk", content: txt });
    appendMsg(data.reply, "bot");

    if (data.score) showScore(data.score);
    if (data.done)  showReport(data.report);

    $btn.prop('disabled', data.done);      // 结束后不再可发
  }

  /* ========= UI 辅助 ========= */
  function appendMsg(text, role) {
    const div = $('<div class="msg ' + role + '"><div class="bubble">' + text + '</div></div>');
    $msg.append(div);
    $msg.scrollTop($msg[0].scrollHeight);
  }

  function showScore(sc) {
    $scoreBody.html(`
      <p>安抚情绪：${sc.emotion}</p>
      <p>信息收集：${sc.info}</p>
      <p>安全意识：${sc.safe}</p>
      <p style="color:#FF5722;margin-top:6px;">${sc.advice}</p>`);
    $score.show();
  }

  function showReport(rep) {
    layer.open({
      type: 1,
      title: "训练小结",
      area: ["620px", "420px"],
      content: `<pre style="padding:20px;margin:0;">${rep}</pre>`
    });
  }

  /* ========= Ajax ========= */
  async function post(body) {
    const res = await fetch(api + "/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ ...body, session_id: sid })
    });
    return await res.json();
  }
});
