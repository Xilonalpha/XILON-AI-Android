package com.xilonalpha.xilonai

/**
 * Compact, bounded 3D scene protocol. No executable model-supplied JavaScript.
 * The phone constructs the Three.js geometry from a small JSON specification.
 */
object XilonStructured3d {
    const val instruction = """For 3D requests output ONLY one compact JSON object, no markdown or HTML:
{"type":"face","color":"#d8a47f","eyeColor":"#46352c","hairColor":"#35251e","width":1.0,"height":1.0}
Supported type: face, sphere, cube, cylinder, cone. Colors are six-digit hex.
width and height are numbers between 0.5 and 2.0. Do not output code or explanations.
For a human head choose type face. This produces a stylized procedural face, not a realistic scan."""

    fun extract(text: String): String? {
        val start = text.indexOf('{')
        val end = text.indexOf('}', start + 1)
        if (start < 0 || end < 0 || end - start > 1200) return null
        val raw = text.substring(start, end + 1)
        // Only accept a complete JSON object: never stop inference on partial fields.
        val parsed = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: return null
        if (!parsed.has("type") || !parsed.has("color") || !parsed.has("width") ||
            !parsed.has("height") || !parsed.has("eyeColor") || !parsed.has("hairColor")) return null
        val type = Regex(""""type"\s*:\s*"(face|sphere|cube|cylinder|cone)"""")
            .find(raw)?.groupValues?.get(1) ?: return null
        fun color(name: String, fallback: String): String {
            val value = Regex(""""$name"\s*:\s*"(#[a-fA-F0-9]{6})"""")
                .find(raw)?.groupValues?.get(1) ?: fallback
            return value
        }
        fun dimension(name: String): Double {
            val value = Regex(""""$name"\s*:\s*([0-9]+(?:\.[0-9]+)?)""")
                .find(raw)?.groupValues?.get(1)?.toDoubleOrNull() ?: 1.0
            return value.coerceIn(0.5, 2.0)
        }
        return """{"type":"$type","color":"${color("color", "#d8a47f")}","eyeColor":"${color("eyeColor", "#46352c")}","hairColor":"${color("hairColor", "#35251e")}","width":${dimension("width")},"height":${dimension("height")}}"""
    }

    fun html(text: String): String? {
        val spec = extract(text) ?: return null
        return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#101522}</style></head>
<body><script src="three.min.js"></script><script>
const spec=$spec;
const scene=new THREE.Scene();scene.background=new THREE.Color(0x101522);
const camera=new THREE.PerspectiveCamera(45,innerWidth/innerHeight,.1,100);
camera.position.set(0,0,5.8);
const renderer=new THREE.WebGLRenderer({antialias:true});
renderer.setSize(innerWidth,innerHeight);document.body.appendChild(renderer.domElement);
scene.add(new THREE.HemisphereLight(0xffffff,0x46516b,2));
const light=new THREE.DirectionalLight(0xffffff,2);light.position.set(3,4,5);scene.add(light);
const root=new THREE.Group();scene.add(root);
function mesh(geometry,color,x=0,y=0,z=0){
 const m=new THREE.Mesh(geometry,new THREE.MeshStandardMaterial({color,roughness:.75}));
 m.position.set(x,y,z);root.add(m);return m;
}
if(spec.type==='face'){
 const skin=spec.color, eye=spec.eyeColor;
 const head=mesh(new THREE.SphereGeometry(1,32,24),skin);head.scale.set(.82*spec.width,1.12*spec.height,.7);
 mesh(new THREE.SphereGeometry(.16,16,12),skin,-.81,0,0);
 mesh(new THREE.SphereGeometry(.16,16,12),skin,.81,0,0);
 for(const x of [-.31,.31]){
  const socket=mesh(new THREE.SphereGeometry(.145,20,14),'#fffaf0',x,.17,.61);
  socket.scale.set(1,.67,.38);
  mesh(new THREE.SphereGeometry(.075,16,12),eye,x,.17,.69);
  const brow=mesh(new THREE.BoxGeometry(.3,.045,.07),spec.hairColor,x,.4,.67);
 }
 const nose=mesh(new THREE.SphereGeometry(.13,16,12),skin,0,-.11,.73);nose.scale.set(.8,1.6,1.4);
 const mouth=mesh(new THREE.TorusGeometry(.19,.018,8,28,Math.PI),'#8e454d',0,-.48,.59);
 mouth.rotation.z=Math.PI;
 const hair=mesh(new THREE.SphereGeometry(.84,24,16),spec.hairColor,0,.62,-.02);
 hair.scale.set(1,.48,.91);
} else {
 const geometries={sphere:new THREE.SphereGeometry(1,32,24),cube:new THREE.BoxGeometry(1.8,1.8,1.8),
 cylinder:new THREE.CylinderGeometry(.8,.8,1.8,32),cone:new THREE.ConeGeometry(1,2,32)};
 const object=mesh(geometries[spec.type],spec.color);
 object.scale.set(spec.width,spec.height,1);
}
let dragging=false,px=0,py=0;
renderer.domElement.addEventListener('pointerdown',e=>{dragging=true;px=e.clientX;py=e.clientY;renderer.domElement.setPointerCapture(e.pointerId)});
renderer.domElement.addEventListener('pointerup',()=>dragging=false);
renderer.domElement.addEventListener('pointermove',e=>{if(dragging){root.rotation.y+=(e.clientX-px)*.01;root.rotation.x+=(e.clientY-py)*.01;px=e.clientX;py=e.clientY}});
function animate(){requestAnimationFrame(animate);renderer.render(scene,camera)}animate();
addEventListener('resize',()=>{camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight)});
</script></body></html>
""".trimIndent()
    }
}
