EXSURGE_GLYPHS ?= ../exsurge/src/Exsurge.Glyphs.js

.PHONY: test generate-glyphs

test:
	swift test
	cd kotlin && ./gradlew test

# Regenerates the glyph catalogs, Swift and Kotlin, from Exsurge's SVG glyph
# outlines. Pin the Exsurge checkout to the revision named in
# THIRD_PARTY_NOTICES.md.
generate-glyphs:
	node Scripts/generate-glyphs.mjs \
		'$(EXSURGE_GLYPHS)' \
		Sources/GABCLayout/GeneratedGregorianGlyphCatalog.swift
	node Scripts/generate-glyphs.mjs \
		'$(EXSURGE_GLYPHS)' \
		kotlin/gabc-layout/src/main/kotlin/io/github/mcmxmcm/gabc/layout/GeneratedGregorianGlyphCatalog.kt
