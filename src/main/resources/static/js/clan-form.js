(function(){
  'use strict';

  //live preview for the create and settings forms, the same inputs on both pages
  var name_input = document.getElementById('clanName');
  var tag_input = document.getElementById('clanTag');
  var desc_input = document.getElementById('clanDesc');
  var public_toggle = document.getElementById('publicToggle');
  if(!name_input || !tag_input || !desc_input || !public_toggle) return;

  var preview_badge = document.getElementById('previewBadgeText');
  var preview_name = document.getElementById('previewName');
  var preview_tag = document.getElementById('previewTag');
  var preview_desc = document.getElementById('previewDesc');
  var preview_status = document.getElementById('previewStatus');

  function update_preview(){
    var name = name_input.value.trim();
    var tag = tag_input.value.trim();
    var desc = desc_input.value.trim();
    //console.log("preview", name, tag);
    if(preview_name) preview_name.textContent = name || 'Your Clan Name';
    if(preview_tag) preview_tag.textContent = '[' + (tag || 'TAG')+']';
    if(preview_badge) preview_badge.textContent = tag || 'TAG';
    if(preview_desc) preview_desc.textContent = desc || 'Your clan description will appear here.';
    var is_public = public_toggle.checked;
    if(preview_status){
      preview_status.textContent = is_public ? 'Public' : 'Private';
      preview_status.classList.toggle('is-public', is_public);
      preview_status.classList.toggle('is-private',!is_public);
    }
  }

  [name_input, desc_input].forEach(function(el){
    el.addEventListener('input', update_preview);
  });
  public_toggle.addEventListener('change', update_preview);

  tag_input.addEventListener('input', function(){
    var cleaned = tag_input.value.replace(/[^a-zA-Z0-9]/g, '').slice(0, 3);// the browser side of the tag rule, the server checks it again with the configured pattern
    if(cleaned !== tag_input.value){ tag_input.value = cleaned; }
    //console.log("tag", cleaned);
    update_preview();
  });

  update_preview();

})();
