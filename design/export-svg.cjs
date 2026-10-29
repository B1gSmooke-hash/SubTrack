// Export visible DOM shapes and text as native editable SVG objects for Figma.
module.exports = function exportSvg() {
 const phone=document.querySelector('#phone'), bounds=phone.getBoundingClientRect(), w=bounds.width,h=bounds.height;
 const esc=s=>String(s).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
 const n=x=>Math.round(x*100)/100, output=[];
 const visibleColor=c=>c!=='rgba(0, 0, 0, 0)'&&c!=='transparent';
 function rect(r,style){const x=r.x-bounds.x,y=r.y-bounds.y; if(y>=h||y+r.height<=0)return;
   if(visibleColor(style.backgroundColor)||parseFloat(style.borderTopWidth)>0)output.push(`<rect x="${n(x)}" y="${n(y)}" width="${n(r.width)}" height="${n(r.height)}" rx="${parseFloat(style.borderTopLeftRadius)||0}" fill="${visibleColor(style.backgroundColor)?style.backgroundColor:'none'}" stroke="${parseFloat(style.borderTopWidth)>0?style.borderTopColor:'none'}" stroke-width="${parseFloat(style.borderTopWidth)||0}"/>`);
 }
 function text(value,x,y,style){output.push(`<text x="${n(x)}" y="${n(y)}" font-family="Arial" font-size="${parseFloat(style.fontSize)}" font-weight="${style.fontWeight}" fill="${style.color}">${esc(value)}</text>`)}
 function walk(el){const style=getComputedStyle(el),r=el.getBoundingClientRect();if(style.display==='none'||style.visibility==='hidden'||!r.width||!r.height)return;
   rect(r,style);
   if(el.classList.contains('green-header')||el.classList.contains('hero')){
     const x=n(r.x-bounds.x),y=n(r.y-bounds.y),ww=n(r.width),hh=n(r.height),id='header'+output.length;
     output.push(`<defs><clipPath id="${id}"><rect x="${x}" y="${y}" width="${ww}" height="${hh}" rx="24"/></clipPath></defs><g clip-path="url(#${id})"><rect x="${x}" y="${y}" width="${ww}" height="${hh}" fill="url(#greenHeader)"/><circle cx="${n(x+ww*1.07)}" cy="${n(y+hh*1.15)}" r="${n(ww*.56)}" fill="#fff" opacity=".045"/><circle cx="${n(x+ww*.96)}" cy="${n(y+hh*1.13)}" r="${n(ww*.44)}" fill="#fff" opacity=".035"/></g>`);
   }
   if(el.tagName.toLowerCase()==='svg'){
     output.push(`<svg x="${n(r.x-bounds.x)}" y="${n(r.y-bounds.y)}" width="${n(r.width)}" height="${n(r.height)}" viewBox="${el.getAttribute('viewBox')}" fill="none" stroke="${style.color}" stroke-width="1.7">${el.innerHTML}</svg>`);return;
   }
   if(el.tagName==='INPUT'&&el.type==='checkbox'){
     if(el.classList.contains('switch'))output.push(`<circle cx="${n(r.x-bounds.x+(el.checked?35:15))}" cy="${n(r.y-bounds.y+15)}" r="10" fill="${el.checked?'#fff':style.borderTopColor}"/>`);
     else output.push(`<rect x="${n(r.x-bounds.x+2)}" y="${n(r.y-bounds.y+2)}" width="16" height="16" rx="3" fill="${el.checked?'#176b50':'none'}" stroke="#61756a"/>`);
     return;
   }
   if(el.classList.contains('ring')) {
     const total=active().reduce((a,s)=>a+month(s)*period,0),cx=r.x-bounds.x+r.width/2,cy=r.y-bounds.y+r.height/2,radius=r.width/2-11.5;let angle=-Math.PI/2;
     for(const cat of categories){const value=active().filter(s=>s.category===cat).reduce((a,s)=>a+month(s)*period,0);if(!value||!total)continue;let end=angle+value/total*2*Math.PI;output.push(`<path d="M ${cx+radius*Math.cos(angle)} ${cy+radius*Math.sin(angle)} A ${radius} ${radius} 0 ${end-angle>Math.PI?1:0} 1 ${cx+radius*Math.cos(end)} ${cy+radius*Math.sin(end)}" fill="none" stroke="${colors[categories.indexOf(cat)]}" stroke-width="23"/>`);angle=end}
   }
   if(el.tagName==='INPUT'&&el.type!=='checkbox'){const value=el.value||el.placeholder;const val=el.type==='password'?'•'.repeat(value.length):value;text(val,r.x-bounds.x+parseFloat(style.paddingLeft),r.y-bounds.y+r.height/2+parseFloat(style.fontSize)*.35,style);return}
   if(el.tagName==='SELECT'){text(el.options[el.selectedIndex].text,r.x-bounds.x+13,r.y-bounds.y+r.height/2+5,style);text('⌄',r.right-bounds.x-24,r.y-bounds.y+r.height/2+5,style);return}
   let clipped=false;if(el.id==='main'){output.push(`<g clip-path="url(#mainclip)">`);clipped=true;}
   for(const child of el.childNodes){if(child.nodeType===Node.ELEMENT_NODE)walk(child);else if(child.nodeType===Node.TEXT_NODE&&child.textContent.trim()){
     const str=child.textContent,groups=[];for(let i=0;i<str.length;i++){const range=document.createRange();range.setStart(child,i);range.setEnd(child,i+1);const q=range.getBoundingClientRect();if(!q.height)continue;let group=groups.find(g=>Math.abs(g.y-q.y)<1);if(!group){group={x:q.x,y:q.y,height:q.height,text:''};groups.push(group)}group.text+=str[i]}
     groups.forEach(g=>text(g.text,g.x-bounds.x,g.y-bounds.y+g.height*.82,style));
   }} if(clipped)output.push('</g>');
 }
 walk(phone);const m=document.querySelector('main').getBoundingClientRect();
 return `<svg xmlns="http://www.w3.org/2000/svg" width="${n(w)}" height="${n(h)}" viewBox="0 0 ${n(w)} ${n(h)}"><title>SubTrack ${esc(document.querySelector('header h2').textContent)}</title><defs><linearGradient id="greenHeader" x2="1" y2="1"><stop stop-color="#125440"/><stop offset="1" stop-color="#1b775b"/></linearGradient><clipPath id="mainclip"><rect x="0" y="${n(m.y-bounds.y)}" width="${n(w)}" height="${n(m.height)}"/></clipPath><clipPath id="screen"><rect width="${n(w)}" height="${n(h)}" rx="28"/></clipPath></defs><g clip-path="url(#screen)">${output.join('')}</g></svg>`;
};
