(() => {
  const gallery = document.getElementById('room-photo-gallery');
  const galleryImage = gallery?.querySelector('.gallery-view');
  const galleryCaption = gallery?.querySelector('.gallery-caption');
  const galleryIndex = gallery?.querySelector('.gallery-index');
  let galleryPhotos = [];
  let galleryPosition = 0;

  const showGalleryPhoto = () => {
    if (!galleryImage || galleryPhotos.length === 0) return;
    const photo = galleryPhotos[galleryPosition];
    galleryImage.src = photo.src;
    galleryImage.alt = photo.alt;
    if (galleryCaption) galleryCaption.textContent = photo.room;
    if (galleryIndex) galleryIndex.textContent = `${galleryPosition + 1} / ${galleryPhotos.length}`;
  };

  const openGallery = (carousel, photos, active) => {
    galleryPhotos = photos.map((photo) => ({
      src: photo.currentSrc || photo.src,
      alt: photo.alt,
      room: carousel.closest('.room-card')?.querySelector('.room-info h3')?.textContent || 'Room photos'
    }));
    galleryPosition = Math.max(0, active);
    showGalleryPhoto();
    gallery?.showModal();
  };

  document.querySelectorAll('[data-room-carousel]').forEach((carousel) => {
    const photos = [...carousel.querySelectorAll('.carousel-photo')];
    const count = carousel.querySelector('.carousel-count');
    const card = carousel.closest('.room-card');
    let active = Math.max(0, photos.findIndex((photo) => photo.classList.contains('is-active')));
    const show = (index) => {
      if (!photos.length) return;
      active = (index + photos.length) % photos.length;
      photos.forEach((photo, i) => photo.classList.toggle('is-active', i === active));
      if (count) count.textContent = `${active + 1} / ${photos.length}`;
    };

    card?.addEventListener('click', (event) => {
      if (event.target.closest('button, a, input, select, textarea, form, .room-availability')) return;
      openGallery(carousel, photos, active);
    });
    card?.addEventListener('keydown', (event) => {
      if (event.target !== card || (event.key !== 'Enter' && event.key !== ' ')) return;
      event.preventDefault();
      openGallery(carousel, photos, active);
    });

    if (photos.length < 2) {
      carousel.querySelectorAll('.carousel-arrow').forEach((button) => button.hidden = true);
      if (count) count.hidden = true;
      return;
    }

    carousel.querySelector('.carousel-prev')?.addEventListener('click', () => show(active - 1));
    carousel.querySelector('.carousel-next')?.addEventListener('click', () => show(active + 1));
    show(active);
    window.setInterval(() => {
      if (document.hidden || gallery?.open || carousel.matches(':hover, :focus-within')) return;
      show(active + 1);
    }, 4000);
  });

  gallery?.querySelector('.gallery-close')?.addEventListener('click', () => gallery.close());
  gallery?.querySelector('.gallery-step-prev')?.addEventListener('click', () => {
    galleryPosition = (galleryPosition + galleryPhotos.length - 1) % galleryPhotos.length;
    showGalleryPhoto();
  });
  gallery?.querySelector('.gallery-step-next')?.addEventListener('click', () => {
    galleryPosition = (galleryPosition + 1) % galleryPhotos.length;
    showGalleryPhoto();
  });
  gallery?.addEventListener('click', (event) => {
    if (event.target === gallery) gallery.close();
  });
})();
