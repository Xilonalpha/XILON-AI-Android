package com.xilonalpha.xilonai

/** Compact offline music-video scene. Audio sync and MP4 export are future stages. */
object XilonMusicVideo {
    const val instruction = """For an animated music video return ONLY JSON:
{"type":"music_video","characters":2,"theme":"neon","bpm":120,"duration":30}
Allowed themes: neon, sunset, space. Characters: 1-3, BPM: 60-180, duration: 5-60.
Do not output HTML, JavaScript or markdown."""

    data class Scene(val characters: Int, val theme: String, val bpm: Int, val duration: Int)

    fun parse(text: String): Scene? {
        val start = text.indexOf('{')
        val end = text.indexOf('}', start + 1)
        if (start < 0 || end <= start || end - start > 800) return null
        val json = text.substring(start, end + 1)
        if (!Regex(""""type"\\s*:\\s*"music_video"""").containsMatchIn(json)) return null
        fun number(key: String, fallback: Int): Int =
            Regex(""""$key"\\s*:\\s*(\\d+)""").find(json)?.groupValues?.get(1)?.toIntOrNull() ?: fallback
        val theme = Regex(""""theme"\\s*:\\s*"(neon|sunset|space)"""")
            .find(json)?.groupValues?.get(1) ?: "neon"
        return Scene(number("characters", 2).coerceIn(1, 3), theme,
            number("bpm", 120).coerceIn(60, 180), number("duration", 30).coerceIn(5, 60))
    }

    fun html(text: String): String? {
        val spec = parse(text) ?: return null
        val bg = when (spec.theme) {
            "sunset" -> "#49243a"
            "space" -> "#080b24"
            else -> "#100c25"
        }
        return """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:$bg}</style></head>
<body><script src="three.min.js"></script><script>
const scene=new THREE.Scene();scene.background=new THREE.Color('$bg');
const camera=new THREE.PerspectiveCamera(48,innerWidth/innerHeight,.1,100);
camera.position.set(0,2.1,8);
const renderer=new THREE.WebGLRenderer({antialias:true});
renderer.setSize(innerWidth,innerHeight);document.body.appendChild(renderer.domElement);
scene.add(new THREE.HemisphereLight(0xffffff,0x404080,2));
const light=new THREE.PointLight(0xff77dd,70);light.position.set(1,5,3);scene.add(light);
const floor=new THREE.Mesh(new THREE.PlaneGeometry(30,30),
 new THREE.MeshStandardMaterial({color:0x242342,roughness:.8}));
floor.rotation.x=-Math.PI/2;floor.position.y=-1.5;scene.add(floor);
const dancers=[];
function part(group,geo,color,x,y,z){
 const mesh=new THREE.Mesh(geo,new THREE.MeshStandardMaterial({color,roughness:.65}));
 mesh.position.set(x,y,z);group.add(mesh);return mesh;
}
for(let i=0;i<${spec.characters};i++){
 const g=new THREE.Group();g.position.x=(i-(${spec.characters}-1)/2)*2.2;scene.add(g);
 const hue=[0x68e1fd,0xff78b8,0xffcc66][i];
 part(g,new THREE.CapsuleGeometry(.33,.95,6,12),hue,0,0,0);
 part(g,new THREE.SphereGeometry(.38,20,16),0xe6b68e,0,1.05,0);
 const left=part(g,new THREE.CapsuleGeometry(.12,.65,4,8),hue,-.52,.12,0);
 const right=part(g,new THREE.CapsuleGeometry(.12,.65,4,8),hue,.52,.12,0);
 const legL=part(g,new THREE.CapsuleGeometry(.13,.5,4,8),0x293c6d,-.19,-.95,0);
 const legR=part(g,new THREE.CapsuleGeometry(.13,.5,4,8),0x293c6d,.19,-.95,0);
 dancers.push({g,left,right,legL,legR});
}
const bpm=${spec.bpm},duration=${spec.duration};
const start=performance.now();
function frame(now){
 const t=(now-start)/1000,beat=t*bpm*Math.PI/30;
 dancers.forEach((d,i)=>{
  d.g.position.y=.12*Math.abs(Math.sin(beat+i));
  d.g.rotation.y=.25*Math.sin(beat*.5+i);
  d.left.rotation.z=.6*Math.sin(beat+i);
  d.right.rotation.z=-.6*Math.sin(beat+i);
  d.legL.rotation.x=.4*Math.sin(beat+i);
  d.legR.rotation.x=-.4*Math.sin(beat+i);
 });
 light.intensity=45+25*Math.abs(Math.sin(beat));
 renderer.render(scene,camera);
 if(t<duration)requestAnimationFrame(frame);
}
requestAnimationFrame(frame);
addEventListener('resize',()=>{camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight)});
</script></body></html>
""".trimIndent()
    }
}
