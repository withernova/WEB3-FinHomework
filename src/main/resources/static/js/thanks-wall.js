/* thanks-wall.js  v8.0  */
layui.use(['layer', 'form'], function () {
  'use strict';

  /* ========= 基础 ========== */
  const layer = layui.layer;
  const form  = layui.form;
  const $wall = $('#wall');
  const storeKey = 'thanks_notes_v8';

  /* ========= 工具：HTML 转义 ========= */
  const htmlEscape = s =>
    s.replace(/[&<>"']/g, m => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;',
      '"': '&quot;', "'": '&#39;',
    }[m]));

  /* ========= 渐变色 ========= */
  const colors = [
    'linear-gradient(135deg,#ff9a9e,#fecfef)',
    'linear-gradient(135deg,#a18cd1,#fbc2eb)',
    'linear-gradient(135deg,#f6d365,#fda085)',
    'linear-gradient(135deg,#fdcbf1,#e6dee9)',
    'linear-gradient(135deg,#96fbc4,#f9f586)',
    'linear-gradient(135deg,#a1c4fd,#c2e9fb)',
    'linear-gradient(135deg,#84fab0,#8fd3f4)',
    'linear-gradient(135deg,#ffecd2,#fcb69f)',
    'linear-gradient(135deg,#cfd9df,#e2ebf0)',
    'linear-gradient(135deg,#ffdde1,#ee9ca7)',
  ];

  /* ========= 记录当前最高层 ========= */
  let topZ = 1;

  /* ========= 随机位置 ========= */
  function randomPos(noteW, noteH) {
    const wallW = $wall.innerWidth();
    const wallH = $wall.innerHeight();
    return {
      x: Math.random() * (wallW - noteW),
      y: Math.random() * (wallH - noteH),
    };
  }

  /* ========= 创建并渲染便签 ========= */
  function addNote(n) {
    /* 若首次生成则分配层级 = 当前最高层 +1 */
    if (typeof n.z !== 'number') n.z = ++topZ;
    else topZ = Math.max(topZ, n.z);

    const bg  = colors[(Math.random() * colors.length) | 0];
    const deg = (Math.random() * 14 - 7).toFixed(1) + 'deg';

    const $el = $(`
      <div class="note" data-id="${n.id}" style="--bg:${bg};--deg:${deg};--z:${n.z}">
        <div class="paper">
          <div class="content">${htmlEscape(n.text)}</div>
          <footer>${htmlEscape(n.name)} · ${n.time}</footer>
        </div>
      </div>`).appendTo($wall)
        .css('z-index', n.z);

    /* 定位：已有坐标用旧值，否则随机一次并保存 */
    if (typeof n.x !== 'number' || typeof n.y !== 'number') {
      const p = randomPos($el.outerWidth(), $el.outerHeight());
      n.x = p.x; n.y = p.y;
      localStorage.setItem(storeKey, JSON.stringify(notes));
    }
    $el.css({ left: n.x, top: n.y });

    /* 启用拖动 */
    makeDraggable($el, n);

    /* 双击删除（可按需移除） */
    $el.on('dblclick', () => {
      if (confirm('删除此便签？')) {
        notes = notes.filter(x => x.id !== n.id);
        localStorage.setItem(storeKey, JSON.stringify(notes));
        $el.remove();
      }
    });
  }

  /* ========= 拖动实现 ========= */
  function makeDraggable($el, noteObj) {
    $el.on('mousedown', e => {
      if (e.which !== 1) return;           // 只响应左键
      const startX = e.pageX, startY = e.pageY;
      const offsetX = startX - parseFloat($el.css('left'));
      const offsetY = startY - parseFloat($el.css('top'));

      /* 拖动开始：提到最前面 */
      noteObj.z = ++topZ;
      $el.addClass('dragging').css('z-index', topZ);

      $(document)
        .on('mousemove.dragNote', e2 => {
          $el.css({ left: e2.pageX - offsetX, top: e2.pageY - offsetY });
        })
        .on('mouseup.dragNote', () => {
          $(document).off('.dragNote');
          $el.removeClass('dragging');

          /* 保存最新坐标与层级 */
          noteObj.x = parseFloat($el.css('left'));
          noteObj.y = parseFloat($el.css('top'));
          localStorage.setItem(storeKey, JSON.stringify(notes));
        });

      return false;    // 防止拖动时选中文字
    });
  }

  /* ========= 50 条默认感谢内容 ========= */
  const defaultTexts = [
    "感谢搜救队员彻夜搜山！",
    "社区志愿者凌晨广播寻人！",
    "热心的哥免费载我们跑线索！",
    "网格员挨家敲门帮找人！",
    "小学生放学后派传单，感动！",
    "外卖小哥沿街张贴海报！",
    "便利店老板借监控回看轨迹！",
    "警犬雨夜跟踪足迹，敬佩！",
    "超市大姐送来热粥暖胃！",
    "电台主播滚动播出信息！",
    "医院值班医生随队检查！",
    "摄影志愿者航拍协助搜索！",
    "驴友队分享山路GPS轨迹！",
    "公交司机提醒乘客留意！",
    "环卫工早班发现线索并上报！",
    "物业深夜开放大厅供休息！",
    "快递站帮打印寻人启事！",
    "民警加班调取街口监控！",
    "志愿者帮老母亲擦药，暖心！",
    "邻居姐姐整夜陪伴安慰！",
    "高校社团百人地毯式搜索！",
    "村干部组织乡亲分组巡田！",
    "无人机手日夜轮班升空！",
    "热心网友实时整理线索表！",
    "工地师傅停工一起帮喊！",
    "面包车师傅提供免费接驳！",
    "商场保安凌晨巡查楼顶！",
    "便利店老板娘端来热水！",
    "公交公司调站台监控回放！",
    "路过叔叔递雨衣防寒！",
    "出租车电台循环播报寻人！",
    "学生志愿者陪爷爷做核酸！",
    "医生随队背着急救包守护！",
    "社工暖心心理疏导，感谢！",
    "报社记者免费刊登寻人启事！",
    "寺庙师傅敲钟提醒信众关注！",
    "渔民清晨划船沿河搜寻！",
    "交警快速协调封控桥面！",
    "阿姨做饭深夜送到现场！",
    "IT志愿者搭建小程序收线索！",
    "物流公司一次性印百张海报！",
    "花店老板捐小夜灯照明搜索！",
    "咖啡店通宵开门给大家歇脚！",
    "药店免费发放防暑药品！",
    "理发店帮设计醒目横幅！",
    "派出所增派巡逻车夜查！",
    "民兵出动冲锋舟沿河巡！",
    "废品场老板帮翻找可疑角落！",
    "中学生徒步两小时送水！",
    "感谢所有温暖同行的你们！",
  ];

  /* ========= 初始化 localStorage ========= */
  let notes = JSON.parse(localStorage.getItem(storeKey) || '[]');

  if (!Array.isArray(notes) || !notes.length) {
    notes = defaultTexts.map((txt, i) => ({
      id: Date.now() + i,
      name: '家属',
      text: txt,
      time: new Date().toLocaleDateString(),
    }));
    localStorage.setItem(storeKey, JSON.stringify(notes));
  }

  /* ========= 初始化最高层 topZ ========= */
  topZ = notes.reduce((max, n) => Math.max(max, n.z || 1), 1);

  /* ========= 渲染 ========= */
  notes.forEach(addNote);

  /* ========= 新留言按钮 ========= */
  $('#addBtn').on('click', () => {
    const html = `
      <form class="layui-form" style="padding:26px 26px 4px;">
        <div class="layui-form-item">
          <input name="name" placeholder="署名(可空)" class="layui-input">
        </div>
        <div class="layui-form-item layui-form-text">
          <textarea name="text" required placeholder="想说的话..." class="layui-textarea"></textarea>
        </div>
        <button class="layui-btn layui-btn-fluid" lay-submit lay-filter="commit">贴 到 墙</button>
      </form>`;
    layer.open({
      type: 1, title: '写下感谢', shadeClose: true,
      area: ['360px', '330px'], content: html,
    });
    form.render();
  });

  /* ========= 提交 ========= */
  form.on('submit(commit)', d => {
    const txt = d.field.text.trim();
    if (!txt) { layer.msg('内容不能为空'); return false; }
    const note = {
      id: Date.now(),
      name: (d.field.name || '家属').slice(0, 12),
      text: txt.slice(0, 160),
      time: new Date().toLocaleDateString(),
    };
    notes.unshift(note);
    localStorage.setItem(storeKey, JSON.stringify(notes));
    addNote(note);
    layer.closeAll();
    return false;
  });
});
