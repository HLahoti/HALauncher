// Progressive enhancement only: the page works fully without this script.
(() => {
  document.documentElement.classList.remove('no-js');

  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  // Reveal sections as they scroll into view.
  const revealables = document.querySelectorAll('.reveal');
  if ('IntersectionObserver' in window && !reduceMotion) {
    const io = new IntersectionObserver((entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('is-visible');
          io.unobserve(entry.target);
        }
      }
    }, { threshold: 0.18, rootMargin: '0px 0px -8% 0px' });
    revealables.forEach((el) => io.observe(el));
  } else {
    revealables.forEach((el) => el.classList.add('is-visible'));
  }

  // Demo video: play only while on screen; respect reduced motion.
  const video = document.querySelector('.hero video');
  if (video) {
    if (reduceMotion) {
      video.removeAttribute('autoplay');
      video.pause();
      video.controls = true;
    } else if ('IntersectionObserver' in window) {
      new IntersectionObserver(([entry]) => {
        if (entry.isIntersecting) video.play().catch(() => {});
        else video.pause();
      }, { threshold: 0.25 }).observe(video);
    }
  }

  // Gallery arrows scroll by roughly one screen's worth of shots.
  const gallery = document.querySelector('.gallery');
  document.querySelectorAll('[data-scroll]').forEach((btn) => {
    btn.addEventListener('click', () => {
      const dir = Number(btn.dataset.scroll);
      gallery.scrollBy({ left: dir * gallery.clientWidth * 0.8, behavior: reduceMotion ? 'auto' : 'smooth' });
    });
  });

  // Copy the checksum.
  const copyBtn = document.querySelector('[data-copy]');
  const shaEl = document.querySelector('[data-sha]');
  if (copyBtn && shaEl && navigator.clipboard) {
    copyBtn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(shaEl.textContent.trim());
        copyBtn.textContent = 'Copied';
      } catch {
        copyBtn.textContent = 'Press and hold to copy';
      }
      setTimeout(() => { copyBtn.textContent = 'Copy'; }, 2000);
    });
  } else if (copyBtn) {
    copyBtn.hidden = true;
  }

  // Keep version, size and checksum in step with the latest GitHub release, so publishing a
  // new release is enough — the page itself never needs editing. Falls back to the values
  // written in the HTML if the API is unreachable or rate-limited.
  fetch('https://api.github.com/repos/HLahoti/HALauncher/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' },
  })
    .then((res) => (res.ok ? res.json() : Promise.reject(res.status)))
    .then((release) => {
      const apk = (release.assets || []).find((a) => a.name === 'HALauncher.apk');
      if (!apk) return;
      const version = (release.tag_name || '').replace(/^v/i, '');
      if (version) setText('[data-version]', `Version ${version}`);
      setText('[data-size]', `${(apk.size / 1048576).toFixed(1)} MB`);
      if (apk.digest && apk.digest.startsWith('sha256:')) {
        setText('[data-sha]', apk.digest.slice('sha256:'.length));
      }
    })
    .catch(() => {});

  function setText(selector, text) {
    document.querySelectorAll(selector).forEach((el) => { el.textContent = text; });
  }
})();
