package com.xilonalpha.xilonai

/** Starts an asynchronous GLTFExporter job inside the isolated offline WebView. */
object XilonSceneGlb {
    val javascript = """
(function(){
 window.__xilonGlbResult=null;
 try {
  const scene=window.xilonScene||window.scene;
  if(!scene || typeof scene.traverse!=='function') throw Error('Expose scene as window.xilonScene');
  if(typeof window.XilonGLTFExporter!=='function') throw Error('GLTFExporter not loaded');
  const exporter=new window.XilonGLTFExporter();
  exporter.parse(scene,function(result){
   try {
    if(!(result instanceof ArrayBuffer)) throw Error('GLB binary was not returned');
    if(result.byteLength>8*1024*1024) throw Error('GLB exceeds 8 MiB limit');
    const bytes=new Uint8Array(result);let binary='';
    for(let i=0;i<bytes.length;i+=16384) {
      binary+=String.fromCharCode.apply(null,bytes.subarray(i,i+16384));
    }
    window.__xilonGlbResult=JSON.stringify({glb:btoa(binary)});
   }catch(e){window.__xilonGlbResult=JSON.stringify({error:String(e.message||e)});}
  },function(error){
    window.__xilonGlbResult=JSON.stringify({error:String(error.message||error)});
  },{binary:true,onlyVisible:true,trs:false});
  return 'started';
 }catch(e){
  window.__xilonGlbResult=JSON.stringify({error:String(e.message||e)});
  return 'error';
 }
})()
""".trimIndent()
    const val poll = "window.__xilonGlbResult || ''"
}
