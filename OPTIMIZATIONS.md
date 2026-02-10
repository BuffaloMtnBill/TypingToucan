# Optimization Roadmap

The following 10 items have been identified to improve the efficiency, memory footprint, and processor usage of Typing Toucan.

1.  **Refactor SoundManager Music Logic [DONE]**: Load music directly from `AssetManager` to prevent double-loading.
2.  **Texture Atlas Consolidation** [DONE]: Consolidated 10 background tiles into `game.atlas`.
 to reduce batch flushes.
3.  **Optimized Weighted Random Selection (O(1))** [DONE]: Track `totalWeight` as a property in `ClassicSource` to avoid per-selection loops.
4.  **Consolidate Graphics Pipeline Phases** [DONE]: Unified `SpriteBatch` and `ShapeRenderer` phases into a single `SpriteBatch` call using `whitePixel`.
5.  **Implement Proper Font Disposal** [DONE]: Verified that all `BitmapFont` and `FreeTypeFontGenerator` objects are correctly disposed across all screens.
6.  **Efficient Queue Logic (Slide Window)** [DONE]: Refactored `TypingQueue` to use a circular buffer (sliding window), eliminating O(N) shifts.
7.  **Pre-calculate Sprite Render Properties** [DONE]: Cached hitbox offsets, origin points, and gap calculations in `Bird` and `Neck` to eliminate per-frame math.
8.  **Memory-Efficient Passage Processing** [DONE]: Refactored `wordWrap` in `TextSnippetSource` to use manual string indexing and a single `StringBuilder`, avoiding allocation-heavy `String.split()` calls.
9.  **Asynchronous High-Score Saving** [DONE]: Offloaded `prefs.flush()` to a background thread in `SaveManager` to prevent main-thread hitches.
10. **Consolidated Animation Math** [DONE]: Reuse a single per-frame `pulse5` and `pulse10` value for all synchronized sine animations.

**Summary**: All planned optimizations have been implemented, significantly reducing draw calls, memory allocations, and per-frame calculations for improved performance on both desktop and mobile.
