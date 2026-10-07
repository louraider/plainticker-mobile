"""
The XML namespace identifiers the brand scripts write into SVGs and Android vector drawables and
look up when they read them back.

A namespace is a name, not an address: the SVG and Android vector formats fix these exact strings,
scheme included, and no parser, renderer or script here ever opens a connection to them. Changing
the scheme would make every file these scripts write invalid. They are defined once so every writer
and reader spells them identically.
"""

# XML namespace identifier, never fetched.
SVG_NS_URI = "http://www.w3.org/2000/svg"  # nosemgrep # nosec # NOSONAR
# XML namespace identifier, never fetched.
ANDROID_NS_URI = "http://schemas.android.com/apk/res/android"  # nosemgrep # nosec # NOSONAR

# ElementTree's Clark notation, `{uri}tag`, for finding elements and attributes in a namespace.
SVG_NS = "{" + SVG_NS_URI + "}"
ANDROID_NS = "{" + ANDROID_NS_URI + "}"
