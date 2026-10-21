import {onBeforeUnmount, onMounted, ref} from 'vue'

/** Share a CSS breakpoint with components that need a different interaction layout. */
export function useMediaQuery(query = '(max-width: 767px)') {
  const media = window.matchMedia(query)
  const matches = ref(media.matches)
  const update = () => { matches.value = media.matches }
  onMounted(() => { update(); media.addEventListener('change', update) })
  onBeforeUnmount(() => media.removeEventListener('change', update))
  return matches
}
