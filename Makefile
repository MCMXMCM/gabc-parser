EXSURGE_GLYPHS ?= ../exsurge/src/Exsurge.Glyphs.js

.PHONY: test generate-glyphs

test:
	swift test

# Regenerates the glyph catalog from Exsurge's SVG glyph outlines. Pin the
# Exsurge checkout to the revision named in THIRD_PARTY_NOTICES.md.
generate-glyphs:
	node Scripts/generate-glyphs.mjs \
		'$(EXSURGE_GLYPHS)' \
		Sources/GABCLayout/GeneratedGregorianGlyphCatalog.swift
