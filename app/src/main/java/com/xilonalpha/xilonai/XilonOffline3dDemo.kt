package com.xilonalpha.xilonai

/** Built-in WebGL smoke test; no CDN, remote resources, or external assets. */
object XilonOffline3dDemo {
    val html = """
<!DOCTYPE html>
<html lang="ro"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;width:100%;height:100%;background:#10152b;color:white}
canvas{width:100%;height:100%;display:block}#error{position:absolute;top:10px;left:10px}</style>
</head><body><div id="error"></div><canvas id="scene"></canvas>
<script>
(function(){
const canvas=document.getElementById('scene'), error=document.getElementById('error');
const gl=canvas.getContext('webgl',{antialias:true});
if(!gl){error.textContent='WebGL nu este disponibil pe acest dispozitiv.';return;}
const vertex='attribute vec3 position;uniform float angle;uniform float aspect;varying vec3 shade;void main(){float c=cos(angle),s=sin(angle);vec3 p=vec3(c*position.x+s*position.z,position.y,-s*position.x+c*position.z);float c2=cos(angle*.7),s2=sin(angle*.7);p=vec3(p.x,c2*p.y-s2*p.z,s2*p.y+c2*p.z);gl_Position=vec4(p.x*.55/aspect,p.y*.55,p.z*.25,1.0);shade=position*.5+.5;}';
const fragment='precision mediump float;varying vec3 shade;void main(){gl_FragColor=vec4(.12+shade.x*.12,.32+shade.y*.32,.8+shade.z*.2,1.0);}';
function shader(type,source){const sh=gl.createShader(type);gl.shaderSource(sh,source);gl.compileShader(sh);if(!gl.getShaderParameter(sh,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(sh));return sh;}
try{
const program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(program);
if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
gl.useProgram(program);
const positions=[
-1,-1,1, 1,-1,1, 1,1,1, -1,1,1,
-1,-1,-1, -1,1,-1, 1,1,-1, 1,-1,-1];
const indices=[0,1,2,0,2,3,1,7,6,1,6,2,7,4,5,7,5,6,4,0,3,4,3,5,3,2,6,3,6,5,4,7,1,4,1,0];
const vb=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,vb);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(positions),gl.STATIC_DRAW);
const loc=gl.getAttribLocation(program,'position');gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,3,gl.FLOAT,false,0,0);
const ib=gl.createBuffer();gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER,ib);gl.bufferData(gl.ELEMENT_ARRAY_BUFFER,new Uint16Array(indices),gl.STATIC_DRAW);
gl.enable(gl.DEPTH_TEST);
const angle=gl.getUniformLocation(program,'angle'),aspect=gl.getUniformLocation(program,'aspect');
function draw(time){canvas.width=canvas.clientWidth*devicePixelRatio;canvas.height=canvas.clientHeight*devicePixelRatio;gl.viewport(0,0,canvas.width,canvas.height);gl.clearColor(.06,.08,.17,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.uniform1f(angle,time*.0006);gl.uniform1f(aspect,canvas.width/canvas.height);gl.drawElements(gl.TRIANGLES,indices.length,gl.UNSIGNED_SHORT,0);requestAnimationFrame(draw);}
requestAnimationFrame(draw);
}catch(e){error.textContent='Eroare WebGL: '+e.message;}
})();
</script></body></html>
""".trimIndent()
}
