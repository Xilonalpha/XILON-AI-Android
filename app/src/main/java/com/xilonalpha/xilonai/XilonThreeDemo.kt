package com.xilonalpha.xilonai

/** Offline Three.js scene using the bundled classic r150 build. */
object XilonThreeDemo {
    val html = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#10152b}</style>
<script src="https://xilon-preview.invalid/three.min.js"></script>
</head><body><script>
if(typeof THREE==='undefined'){document.body.textContent='Three.js indisponibil';}
else {
const scene=new THREE.Scene();scene.background=new THREE.Color(0x10152b);
const camera=new THREE.PerspectiveCamera(60,1,.1,100);camera.position.z=4;
const renderer=new THREE.WebGLRenderer({antialias:true});
document.body.appendChild(renderer.domElement);
const cube=new THREE.Mesh(new THREE.BoxGeometry(1.7,1.7,1.7),
new THREE.MeshNormalMaterial());scene.add(cube);
function draw(){const w=innerWidth,h=innerHeight;renderer.setSize(w,h,false);
camera.aspect=w/h;camera.updateProjectionMatrix();
cube.rotation.x+=.009;cube.rotation.y+=.014;renderer.render(scene,camera);
requestAnimationFrame(draw);}draw();
}
</script></body></html>
""".trimIndent()
}
