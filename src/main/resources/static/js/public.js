$(function(){
	window.getOldManUrl = "http://localhost:8080//gui/jia/getOldManInfo";
	var time=setTimeout(function(){
		$("#duobei").css({
			"-webkit-transform": "rotate(-30deg) scale(0.5)",
		});
	},2000);

	$("#title").hide();
	var title=setTimeout(function(){
		$("#title").addClass("title_move");
		$("#title").show();
	},400);

	var duobeiWrap=setTimeout(function(){
	},3400);

	$(".cancle").click(function(){
		window.history.go(-1);
		return false;
	});

});
