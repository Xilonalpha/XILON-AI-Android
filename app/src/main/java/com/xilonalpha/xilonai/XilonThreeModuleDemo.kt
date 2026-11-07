package com.xilonalpha.xilonai

/** Offline ES-module import-map compatibility demonstration. */
object XilonThreeModuleDemo {
    val html = """
<!DOCTYPE html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;width:100%;height:100%;background:#10152b;overflow:hidden}</style>
</head><body>
<script type="module">
import {Scene, PerspectiveCamera, WebGLRenderer, Mesh, BoxGeometry, MeshNormalMaterial, Color} from 'three';
const scene=new Scene();window.xilonScene=scene;scene.background=new Color(0x10152b);
const camera=new PerspectiveCamera(60,1,.1,100);camera.position.z=4;
const renderer=new WebGLRenderer({antialias:true});document.body.appendChild(renderer.domElement);
const cube=new Mesh(new BoxGeometry(1.5,1.5,1.5),new MeshNormalMaterial());scene.add(cube);
function frame(){const w=innerWidth,h=innerHeight;renderer.setSize(w,h,false);
camera.aspect=w/h;camera.updateProjectionMatrix();
cube.rotation.y+=.015;cube.rotation.x+=.008;renderer.render(scene,camera);
requestAnimationFrame(frame);}frame();
</script></body></html>
""".trimIndent()
}
