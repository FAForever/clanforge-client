(function(){
  'use strict';

  var menu_toggle = document.getElementById('menuToggle');
  var primary_nav = document.getElementById('primaryNav'); //mobile nav toggle, the same on every page so it lives here once

  function close_nav(){
    if(!primary_nav || !menu_toggle) return;
    primary_nav.classList.remove('is-open');
    menu_toggle.classList.remove('is-open');
    menu_toggle.setAttribute('aria-expanded','false');
  }
  if(menu_toggle && primary_nav){
    menu_toggle.addEventListener('click', function(){
      var is_open = primary_nav.classList.toggle('is-open');
      menu_toggle.classList.toggle('is-open', is_open);
      menu_toggle.setAttribute('aria-expanded', String(is_open));
    });
    primary_nav.querySelectorAll('a').forEach(function(link){
      link.addEventListener('click',close_nav);
    });
  }

  // popovers, the bell and the account menu
  function setup_popover(btn_id,panel_id){
    var btn = document.getElementById(btn_id);
    var panel = document.getElementById(panel_id);
    if(!btn || !panel) return;
    btn.addEventListener('click', function(e){
      e.stopPropagation();
      var will_open = !panel.classList.contains('is-open');
      document.querySelectorAll('.popover.is-open').forEach(function(p){ p.classList.remove('is-open'); });
      document.querySelectorAll('.icon-btn[aria-expanded="true"]').forEach(function(b){ b.setAttribute('aria-expanded','false'); });
      if(will_open){
        panel.classList.add('is-open');
        btn.setAttribute('aria-expanded','true');
      }
    });
  }
  setup_popover('notifBtn','notifPanel');
  setup_popover('avatarBtn','avatarPanel');

  document.addEventListener('click', function(e){
    document.querySelectorAll('.popover.is-open').forEach(function(p){
      if(!p.parentElement.contains(e.target)){
        p.classList.remove('is-open');
        var btn = p.parentElement.querySelector('.icon-btn');
        if(btn) btn.setAttribute('aria-expanded','false');
      }
    });
  });
  document.addEventListener('keydown', function(e){
    if(e.key === 'Escape'){
      document.querySelectorAll('.popover.is-open').forEach(function(p){ p.classList.remove('is-open'); });
      close_nav();
    }
  });

  // a form with data-confirm asks before it posts, which is every remove, leave, transfer and delete
  document.querySelectorAll('form[data-confirm]').forEach(function(form){
    form.addEventListener('submit', function(e){
      if(!window.confirm(form.getAttribute('data-confirm'))) e.preventDefault();
    });
  });

})();
