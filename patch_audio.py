import re
path = '/storage/emulated/0/Download/play/www/index.html'
with open(path, 'r', encoding='utf-8') as f:
    html = f.read()

old_play = '''  function playCurrent(){
    if(state.queueIndex<0 || state.queueIndex>=state.queue.length) return;
    const id = state.queue[state.queueIndex];
    const track = state.library.find(t=>t.id===id);
    if(!track) return;
    state.currentId = id;

    const fsUrl = 'file:///storage/emulated/0/' + track.uri;
    audio.src = fsUrl;
    audio.play().then(()=>{
      updateMini(); updatePlayerUI(); render();
    }).catch(e=>{
      toast('Falha ao tocar: '+e.message);
    });
  }'''

new_play = '''  async function playCurrent(){
    if(state.queueIndex<0 || state.queueIndex>=state.queue.length) return;
    const id = state.queue[state.queueIndex];
    const track = state.library.find(t=>t.id===id);
    if(!track) return;
    state.currentId = id;
    updateMini(); updatePlayerUI(); render();

    const fs = await getFilesystem();
    if(!fs){ toast('Filesystem indisponivel'); return; }

    try {
      toast('Carregando...', 1000);
      const res = await fs.readFile({
        path: track.uri,
        directory: 'EXTERNAL_STORAGE'
      });
      const base64 = res.data;

      const lower = track.uri.toLowerCase();
      let mime = 'audio/mpeg';
      if(lower.endsWith('.m4a') || lower.endsWith('.aac')) mime = 'audio/mp4';
      else if(lower.endsWith('.ogg') || lower.endsWith('.opus')) mime = 'audio/ogg';
      else if(lower.endsWith('.wav')) mime = 'audio/wav';
      else if(lower.endsWith('.flac')) mime = 'audio/flac';

      const byteChars = atob(base64);
      const byteNums = new Array(byteChars.length);
      for (let i = 0; i < byteChars.length; i++) {
        byteNums[i] = byteChars.charCodeAt(i);
      }
      const byteArray = new Uint8Array(byteNums);
      const blob = new Blob([byteArray], { type: mime });
      const url = URL.createObjectURL(blob);

      audio.src = url;
      audio.play().then(()=>{
        updateMini(); updatePlayerUI();
      }).catch(e=>{
        toast('Falha ao tocar: ' + e.message);
      });
    } catch(e) {
      toast('Erro ao carregar arquivo: ' + (e.message || e));
    }
  }'''

if old_play not in html:
    print("ERRO: funcao playCurrent nao encontrada no index.html")
    exit(1)

html = html.replace(old_play, new_play)

with open(path, 'w', encoding='utf-8') as f:
    f.write(html)

print("OK: playCurrent atualizada com sucesso")
