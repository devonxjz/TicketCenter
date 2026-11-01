// Compatibility entry point: the 24 root pages are the source of each frame.
// Use the same mapping and path conversion as sync_frames.js to prevent stale text.
require('./sync_frames').syncFrames();
