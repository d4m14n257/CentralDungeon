import { useMutation } from '@tanstack/react-query'

import { filesApi } from './filesApi'

/**
 * Downloading a file's content and handing it to the browser to save.
 *
 * A mutation and not a query, even though it reads: it happens because somebody clicked, its result
 * is not something to cache, and the backend stamps `lastUsedAt` on every one of them (#75).
 *
 * A plain `<a href>` cannot do this — the endpoint is authenticated with a bearer token the browser
 * never attaches on its own — so the bytes are fetched and released through an object URL.
 *
 * ⚠️ **The URL is revoked on a later task, never in the same one as the click.** Revoking it right
 * after `click()` reads as tidy and silently cancels the download: the browser only *starts* fetching
 * the blob once the click's task finishes, and by then the URL it was pointing at is gone. It cost
 * a green e2e to find, because nothing throws — the download simply never happens. The blob is held
 * for one extra tick, which is the price of it working at all.
 *
 * @returns the mutation, taking the file id and the name to save it under
 */
export function useDownloadFile() {
  return useMutation({
    mutationFn: async ({ fileId, filename }: { fileId: string; filename: string }) => {
      const downloaded = await filesApi.download(fileId, filename)
      const url = URL.createObjectURL(downloaded.blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = downloaded.filename
      document.body.appendChild(anchor)
      anchor.click()
      setTimeout(() => {
        anchor.remove()
        URL.revokeObjectURL(url)
      }, 0)
      return downloaded
    },
  })
}
