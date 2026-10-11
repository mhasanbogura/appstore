/* App Store web — all app data, descriptions and icons load live from Google Drive. */
(function () {
  'use strict';

  var API_KEY = 'AIzaSyAX7T6Vd75LnhQg15IydOLEYqjfGUT8TO8';
  var ROOT_FOLDER = '1PBrhSIvDk0QrgNS6XeTeA1RDLFPeTqKV';
  var API = 'https://www.googleapis.com/drive/v3/files';

  var state = { apps: [], platform: 'Android', query: '' };

  function $(id) { return document.getElementById(id); }

  function toast(msg) {
    var t = $('toast');
    t.textContent = msg;
    t.hidden = false;
    clearTimeout(t._h);
    t._h = setTimeout(function () { t.hidden = true; }, 2600);
  }

  function esc(s) {
    return String(s == null ? '' : s)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  function fmtSize(bytes) {
    if (!bytes || bytes <= 0) return '';
    var mb = bytes / (1024 * 1024);
    if (mb >= 1000) return (mb / 1024).toFixed(2) + ' GB';
    return (mb >= 10 ? Math.round(mb * 10) / 10 : Math.round(mb * 100) / 100) + ' MB';
  }

  function driveGet(params) {
    var qs = Object.keys(params).map(function (k) {
      return encodeURIComponent(k) + '=' + encodeURIComponent(params[k]);
    }).join('&');
    return fetch(API + '?' + qs).then(function (r) {
      if (!r.ok) throw new Error('Drive error ' + r.status);
      return r.json();
    });
  }

  function listAll(query) {
    var out = [];
    function page(token) {
      var p = {
        q: query,
        key: API_KEY,
        fields: 'files(id,name,mimeType,size,modifiedTime,thumbnailLink,iconLink),nextPageToken',
        pageSize: 1000
      };
      if (token) p.pageToken = token;
      return driveGet(p).then(function (d) {
        out = out.concat(d.files || []);
        if (d.nextPageToken) return page(d.nextPageToken);
        return out;
      });
    }
    return page(null);
  }

  function findSubfolder(parentId, name) {
    return driveGet({
      q: "'" + parentId + "' in parents and mimeType = 'application/vnd.google-apps.folder' and name = '" + name + "' and trashed = false",
      key: API_KEY,
      fields: 'files(id)'
    }).then(function (d) {
      return (d.files && d.files[0]) ? d.files[0].id : null;
    });
  }

  function stripExt(name, exts) {
    for (var i = 0; i < exts.length; i++) {
      if (name.toLowerCase().endsWith(exts[i])) return name.slice(0, -exts[i].length);
    }
    return name;
  }

  function parseFile(f, apiKey, iconMap, mdMap) {
    var stripped = stripExt(f.name, ['.apk', '.zip']);
    var verName = '1.0', verCode = 1;

    // First _v match after the package name (mirrors the Android app)
    var vm = null, m, re = /_v([^_]+)/g;
    while ((m = re.exec(stripped)) !== null) {
      if (stripped.slice(0, m.index).indexOf('.') !== -1) { vm = m[1]; break; }
    }
    if (vm) {
      verName = vm;
      var code = parseInt(vm.replace(/\./g, ''), 10);
      if (!isNaN(code)) verCode = code;
    }
    var bm = stripped.match(/_(?:build|buid)_(\d+)/i);
    if (bm) verCode = parseInt(bm[1], 10) || verCode;

    var before = vm ? stripped.slice(0, stripped.indexOf('_v' + vm)).replace(/_+$/, '') : stripped;
    var key = before;
    if (!(mdMap[key] || iconMap[key])) {
      var cand = Object.keys(mdMap).concat(Object.keys(iconMap)).find(function (k) {
        return before.indexOf(k) === 0 && before.length > k.length && !/[A-Za-z0-9]/.test(before[k.length]);
      });
      if (cand) key = cand;
    }

    var segs = before.split('_'), pkgIdx = -1;
    for (var i = segs.length - 1; i >= 0; i--) {
      if (segs[i].indexOf('.') !== -1 && segs[i].indexOf(' ') === -1) { pkgIdx = i; break; }
    }
    var pkg = pkgIdx >= 0
      ? segs.slice(pkgIdx).join('.')
      : ('com.drive.app.' + segs.join('').toLowerCase().replace(/\+/g, 'plus').replace(/ /g, ''));
    var name = pkgIdx > 0 ? segs.slice(0, pkgIdx).join(' ').trim() : before.replace(/_/g, ' ').trim();

    var icon = iconMap[key] || iconMap[stripExt(f.name, ['.apk', '.zip'])] ||
      f.thumbnailLink || f.iconLink || '';
    var mdId = mdMap[key] || mdMap[stripExt(f.name, ['.apk', '.zip'])] || null;

    return {
      id: f.id, packageName: pkg, name: name || f.name,
      versionName: verName, versionCode: verCode,
      size: parseInt(f.size || '0', 10) || 0,
      fileName: f.name, mdId: mdId, iconUrl: icon,
      downloadUrl: 'https://drive.google.com/uc?export=download&id=' + f.id
    };
  }

  function parseFolder(folderId, platform) {
    return listAll("'" + folderId + "' in parents and trashed = false").then(function (files) {
      var isWin = platform === 'Windows';
      var apps = files.filter(function (f) {
        return isWin
          ? (f.mimeType === 'application/zip' || /\.zip$/i.test(f.name))
          : (f.mimeType === 'application/vnd.android.package-archive' || /\.apk$/i.test(f.name));
      });
      var iconMap = {}, mdMap = {};
      files.forEach(function (f) {
        if ((f.mimeType || '').indexOf('image/') === 0) {
          iconMap[f.name.replace(/\.(png|PNG|jpg|jpeg|webp)$/, '').trim()] =
            API + '/' + f.id + '?alt=media&key=' + API_KEY;
        } else if (/\.md$/i.test(f.name)) {
          mdMap[f.name.replace(/\.md$/i, '').trim()] = f.id;
        }
      });
      return apps.map(function (f) { return parseFile(f, API_KEY, iconMap, mdMap); });
    });
  }

  function dedupe(list) {
    var byPkg = {};
    list.forEach(function (a) {
      if (!byPkg[a.packageName] || a.id.length > byPkg[a.packageName].id.length) byPkg[a.packageName] = a;
    });
    return Object.keys(byPkg).map(function (k) { return byPkg[k]; });
  }

  function loadApps() {
    var stats = $('statsLine'), list = $('appList'), empty = $('emptyState');
    stats.textContent = 'Loading apps...';
    list.innerHTML = '';
    empty.hidden = true;
    $('pullHint').hidden = false;

    findSubfolder(ROOT_FOLDER, state.platform).then(function (subId) {
      var jobs = [parseFolder(ROOT_FOLDER, state.platform)];
      if (subId) jobs.push(parseFolder(subId, state.platform));
      return Promise.all(jobs);
    }).then(function (parts) {
      var all = dedupe(parts[0].concat(parts[1] || []));
      all.sort(function (a, b) { return a.name.localeCompare(b.name); });
      state.apps = all;
      render();
    }).catch(function (e) {
      stats.textContent = 'Failed to load apps. Check connection and retry.';
      toast('Load failed: ' + e.message);
    }).finally(function () {
      $('pullHint').hidden = true;
    });
  }

  function render() {
    var q = state.query.trim().toLowerCase();
    var list = state.apps.filter(function (a) {
      return !q || a.name.toLowerCase().indexOf(q) !== -1 || a.packageName.toLowerCase().indexOf(q) !== -1;
    });
    $('statsLine').textContent = state.apps.length + ' apps • updated live from Google Drive';
    $('emptyState').hidden = list.length !== 0;
    $('appList').innerHTML = list.map(function (a, i) {
      return '<div class="app-card" data-i="' + i + '">' +
        (a.iconUrl ? '<img class="app-icon" loading="lazy" src="' + esc(a.iconUrl) + '" alt="">' :
          '<div class="app-icon" style="display:flex;align-items:center;justify-content:center;font-weight:700;font-size:22px;color:var(--primary)">' + esc(a.name.charAt(0).toUpperCase()) + '</div>') +
        '<div class="app-info"><div class="app-name">' + esc(a.name) + '</div>' +
        '<div class="app-pkg">' + esc(a.packageName) + '</div>' +
        '<div class="app-ver">v' + esc(a.versionName) + (a.size ? ' • ' + esc(fmtSize(a.size)) : '') + '</div></div>' +
        '<a class="btn-download" href="' + esc(a.downloadUrl) + '" target="_blank" rel="noopener" data-dl="' + i + '">Download</a>' +
        '</div>';
    }).join('');
    // stash filtered list for modal lookup
    render._list = list;
  }

  function fetchMd(mdId) {
    return fetch(API + '/' + mdId + '?alt=media&key=' + API_KEY).then(function (r) {
      if (!r.ok) throw new Error('md ' + r.status);
      return r.text();
    });
  }

  function openModal(app) {
    $('mIcon').src = app.iconUrl || 'icons/icon.png';
    $('mName').textContent = app.name;
    $('mPkg').textContent = app.packageName;
    $('mVer').textContent = 'v' + app.versionName + (app.size ? ' • ' + fmtSize(app.size) : '');
    $('mDownload').href = app.downloadUrl;
    $('mDesc').textContent = 'Loading description...';
    $('modal').hidden = false;
    document.body.style.overflow = 'hidden';
    if (app.mdId) {
      fetchMd(app.mdId).then(function (t) { $('mDesc').textContent = t.trim() || 'No description.'; })
        .catch(function () { $('mDesc').textContent = 'No description.'; });
    } else {
      $('mDesc').textContent = 'Discovered APK from Google Drive: ' + app.fileName + '.';
    }
  }

  function closeModal() {
    $('modal').hidden = true;
    document.body.style.overflow = '';
  }

  function setTheme(t) {
    document.documentElement.setAttribute('data-theme', t);
    try { localStorage.setItem('appstore-theme', t); } catch (e) {}
    document.querySelectorAll('#themeRow .theme-btn').forEach(function (b) {
      b.classList.toggle('active', b.getAttribute('data-theme') === t);
    });
  }

  function show(view) {
    $('view-home').hidden = view !== 'home';
    $('view-settings').hidden = view !== 'settings';
    $('btnSettings').style.opacity = view === 'settings' ? '0.45' : '1';
    window.scrollTo(0, 0);
  }

  // ---------- events ----------
  document.querySelectorAll('.chip').forEach(function (c) {
    c.addEventListener('click', function () {
      document.querySelectorAll('.chip').forEach(function (x) { x.classList.remove('active'); });
      c.classList.add('active');
      state.platform = c.getAttribute('data-platform');
      loadApps();
    });
  });

  var searchT = null;
  $('searchInput').addEventListener('input', function (e) {
    clearTimeout(searchT);
    searchT = setTimeout(function () { state.query = e.target.value; render(); }, 250);
  });

  $('appList').addEventListener('click', function (e) {
    if (e.target.closest('a')) return; // let Download link work
    var card = e.target.closest('.app-card');
    if (!card) return;
    var app = (render._list || [])[parseInt(card.getAttribute('data-i'), 10)];
    if (app) openModal(app);
  });

  $('modalClose').addEventListener('click', closeModal);
  $('modal').addEventListener('click', function (e) { if (e.target === $('modal')) closeModal(); });
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') closeModal(); });

  var btnRefresh = $('btnRefresh');
  btnRefresh.addEventListener('click', function () {
    btnRefresh.classList.add('spinning');
    loadApps();
    setTimeout(function () { btnRefresh.classList.remove('spinning'); }, 1200);
  });

  $('btnSettings').addEventListener('click', function () {
    show($('view-settings').hidden ? 'settings' : 'home');
  });
  $('btnBrand').addEventListener('click', function () { show('home'); });

  document.querySelectorAll('#themeRow .theme-btn').forEach(function (b) {
    b.addEventListener('click', function () { setTheme(b.getAttribute('data-theme')); });
  });

  // ---------- init (no login) ----------
  var savedTheme = 'light';
  try { savedTheme = localStorage.getItem('appstore-theme') || 'light'; } catch (e) {}
  setTheme(savedTheme);
  loadApps();
})();
