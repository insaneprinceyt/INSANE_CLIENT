# INSANE CLIENT logo

The emerald and glacier-white IC monogram is the launcher identity. `insane-mark.svg`
is the editable geometry; `insane-icon.svg` adds the dark app-icon background.
The design was developed with image generation and rebuilt as clean vector paths
so it remains sharp in Android UI and at small sizes.

Regenerate Android and website assets with Node.js and the `sharp` package installed:

```sh
node scripts/generate_brand_assets.cjs
```

The generator exports all five Android density buckets, adaptive vectors,
Android 13 monochrome icons, white notification silhouettes, and store artwork.
Adaptive foreground geometry stays within a 66dp circle centered on its 108dp
canvas. The notification mark uses only transparency and white. Its background
is supplied by Android.

Keep the existing `ic_hyper_full` resource identifier: About and default instance
icons reference it, but its artwork is the INSANE CLIENT monogram. Loader and
user-selected instance icons are separate and are not generated here.
