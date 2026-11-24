import { useQuery } from '@tanstack/react-query'

import { queryKeys } from '@/api/queryKeys'

import { filesApi } from './filesApi'

/**
 * A file's bytes, fetched so the preview dialog can draw them.
 *
 * The same authenticated download a save-to-disk uses, because the content endpoint needs the bearer
 * token and an `<img src>` or an iframe cannot send it. It is a query, unlike the download, since
 * opening a preview is a read the dialog renders from; but nothing of it is worth keeping: with
 * `gcTime: 0` the blob leaves the cache the moment the dialog's body unmounts, and the next open fetches again
 * (the backend stamps the use on every fetch, which is the intended record that somebody looked).
 *
 * @param fileId  the file
 * @param name    what to call it if the response carries no filename
 * @returns the query for the downloaded file
 */
export function useFilePreview(fileId: string, name: string) {
  return useQuery({
    queryKey: queryKeys.files.preview(fileId),
    queryFn: () => filesApi.download(fileId, name),
    staleTime: 0,
    gcTime: 0,
  })
}
