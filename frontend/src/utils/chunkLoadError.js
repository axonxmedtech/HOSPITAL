/**
 * True when a lazy-loaded page chunk could not be fetched. This happens to users whose tab
 * was open across a deploy: the old hashed chunk no longer exists on the server.
 */
export const isChunkLoadError = (error) =>
  /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module|ChunkLoadError|Loading chunk [\w-]+ failed/i.test(
    `${error?.name || ''} ${error?.message || ''}`
  );
