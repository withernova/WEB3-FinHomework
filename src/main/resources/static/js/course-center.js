layui.use(['table','layer'], function(){
  const table = layui.table;
  const layer = layui.layer;

  /* ===== 课程数据（本地静态路径） ===== */
  const courses = [
    {id:1,title:"急救三角巾包扎",cat:"急救",type:"video",
      url:"/course-files/bandage.mp4", duration:"6:41"},
    {id:2,title:"和老人沟通技巧",cat:"沟通",type:"video",
      url:"/course-files/laorengoutong.mp4",   duration:"3:26"},
    {id:3,title:"“黄金四分钟”心肺复苏",cat:"急救",type:"video",
      url:"/course-files/xinfeifusu.mp4",    duration:"4:30"},
    {id:4,title:"与失智老人沟通法",cat:"沟通",type:"video",
      url:"/course-files/shizhilaoren.mp4",    duration:"2:43"},
    {id:5,title:"对讲机使用教学",cat:"设备",type:"video",
      url:"/course-files/duijiangji.mp4",    duration:"3:48"},
    {id:6,title:"徒步搜救省力走姿",cat:"体能",type:"video",
      url:"/course-files/tubu.mp4",    duration:"1:22"},
    {id:7,title:"山地绳索基础打结",cat:"技术",type:"video",
      url:"/course-files/moutain.mp4",    duration:"8:01"},
    {id:8,title:"急救担架搬运",cat:"急救",type:"video",
      url:"/course-files/danjia.mp4",    duration:"1:17"},
    {id:9,title:"创口止血压迫包扎",cat:"急救",type:"video",
      url:"/course-files/baoza.mp4",    duration:"18:18"},
    {id:10,title:"雨季救援触电防范",cat:"安全",type:"video",
      url:"/course-files/yujichudian.mp4",    duration:"1:59"}
  ];
  init(courses);

  /* 如果将来换成接口：fetch('/api/courses').then(r=>r.json()).then(init); */

  function init(data){
    table.render({
      elem:'#courseTable',
      height:460,
      data:data,
      page:{
            limit:10,                             // 每页 10 条
            layout:['prev','page','next','limit','skip'] // ← 去掉 count
        },
      cols:[[
        {field:'id',title:'ID',width:60,sort:true},
        {field:'title',title:'课程标题',minWidth:260},
        {field:'cat',title:'分类',width:100},
        {field:'duration',title:'时长',width:90},
        {title:'操作',width:100,align:'center',
          templet:()=>'<a class="layui-btn layui-btn-xs" lay-event="play">播放</a>'}
      ]]
    });

    // tool 事件监听，filter 必须与 lay-filter 保持一致
    table.on('tool(courseTable)', obj=>{
      if(obj.event === 'play') openCourse(obj.data);
    });
  }

  /* 弹窗播放 */
  function openCourse(c){
    if(c.type === 'video'){
      layer.open({
        type:1,title:c.title,shadeClose:true,
        area:['800px','460px'],
        content:`<video src="${c.url}" controls autoplay style="width:100%;height:100%"></video>`
      });
    }else if(c.type === 'pdf'){
      layer.open({
        type:1,title:c.title,shadeClose:true,
        area:['900px','600px'],
        content:`<iframe src="${c.url}" style="width:100%;height:100%;border:none;"></iframe>`
      });
    }else{
      layer.msg('未知文件类型',{icon:2});
    }
  }
});
