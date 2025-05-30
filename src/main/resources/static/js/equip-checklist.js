layui.use(['layer','form'], function () {
  const layer = layui.layer;
  const form  = layui.form;

  /* 打开弹窗 */
  $('#equipGenBtn').on('click', () => {
    layer.open({
      type:1,
      title:'选择随身装备',
      area:['460px','auto'],
      content: $('#equipModal')
    });
  });

  /* 生成 PDF */
  form.on('submit(equipConfirm)', async function (data) {
    const raw  = data.field.items;
    const list = Array.isArray(raw) ? raw : (raw ? [raw] : []);
    if(!list.length){ layer.msg('请至少勾选一项',{icon:0}); return false; }

    // 构造隐藏 DOM
    const box = document.createElement('div');
    box.style.width='400px';
    box.innerHTML = `
      <h2 style="text-align:center;margin:0 0 12px;font-size:18px;">
        志愿者个人装备自查清单
      </h2>
      <p style="font-size:12px;margin:0 0 10px;">
        生成时间：${new Date().toLocaleString()}
      </p>
      <ul style="padding-left:20px;font-size:14px;line-height:1.8;margin:0;">
        ${list.map((t,i)=>`<li>${i+1}. ${t}</li>`).join('')}
      </ul>`;
    document.body.appendChild(box);

    // html2canvas 转图
    const canvas = await html2canvas(box, {scale:2});
    document.body.removeChild(box);

    // jsPDF 输出 A5
    const { jsPDF } = window.jspdf;
    const doc = new jsPDF({format:'a5',unit:'mm'});
    const pageW = doc.internal.pageSize.getWidth();
    const imgW  = pageW - 20;                 // 左右 10 mm 边距
    const imgH  = canvas.height * imgW / canvas.width;
    const imgData = canvas.toDataURL('image/jpeg',1.0);

    doc.addImage(imgData,'JPEG',10,10,imgW,imgH);
    doc.save('装备清单.pdf');
    return false;
  });
});
