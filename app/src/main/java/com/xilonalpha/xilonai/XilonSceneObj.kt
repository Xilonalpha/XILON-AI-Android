package com.xilonalpha.xilonai

/**
 * Runs inside an explicitly opened offline preview. Exports visible mesh geometry
 * from window.xilonScene (or window.scene) into OBJ, applying world transforms.
 * Does not export materials, animations, morph targets, or textures.
 */
object XilonSceneObj {
    val javascript = """
(function() {
 try {
  const scene=window.xilonScene||window.scene;
  if(!scene || typeof scene.traverse!=='function') return JSON.stringify({error:'Scena nu este expusă ca window.xilonScene sau window.scene'});
  scene.updateMatrixWorld(true);
  const lines=['# XILON scene export'];let offset=0;
  scene.traverse(function(mesh){
   if(!mesh.isMesh || !mesh.geometry || mesh.visible===false) return;
   if(mesh.material && Array.isArray(mesh.material)) throw Error('Multi-material meshes require glTF exporter');
   const g=mesh.geometry, pos=g.getAttribute('position');
   if(g.morphAttributes && Object.keys(g.morphAttributes).length) throw Error('Morph targets require glTF exporter');
   if(!pos || pos.count<3) return;
   const v=new THREE.Vector3();
   lines.push('o '+String(mesh.name||'Mesh').replace(/[^a-zA-Z0-9_-]/g,'_'));
   for(let i=0;i<pos.count;i++){
    v.fromBufferAttribute(pos,i).applyMatrix4(mesh.matrixWorld);
    lines.push('v '+v.x+' '+v.y+' '+v.z);
   }
   const index=g.getIndex(), count=index?index.count:pos.count;
   if(count%3!==0) throw Error('Non-triangle geometry cannot be exported');
   for(let i=0;i+2<count;i+=3){
    const a=index?index.getX(i):i,b=index?index.getX(i+1):i+1,c=index?index.getX(i+2):i+2;
    lines.push('f '+(offset+a+1)+' '+(offset+b+1)+' '+(offset+c+1));
   }
   offset+=pos.count;
  });
  if(offset===0) return JSON.stringify({error:'Scena nu conține geometrie Mesh exportabilă'});
  return JSON.stringify({obj:lines.join('\n')+'\n'});
 } catch(e) {return JSON.stringify({error:String(e.message||e)});}
})()
""".trimIndent()
}
