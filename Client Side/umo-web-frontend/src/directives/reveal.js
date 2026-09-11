const REVEAL_SELECTOR = '[data-reveal]'

function revealElement(element) {
  element.classList.add('is-revealed')
}

export const reveal = {
  mounted(element) {
    element.dataset.reveal = element.dataset.reveal || ''

    if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) {
      revealElement(element)
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            revealElement(element)
            observer.disconnect()
          }
        }
      },
      { rootMargin: '0px 0px -10% 0px', threshold: 0.12 },
    )

    observer.observe(element)
    element.__revealObserver = observer
  },
  unmounted(element) {
    element.__revealObserver?.disconnect()
  },
}

export { REVEAL_SELECTOR }
