# Shared by debloat.sh and restore.sh. Source it, don't run it.
#
# Every value here ends up inside `adb shell ...`, which hands the joined words to a shell on the
# device. A list line like `foo;reboot` would run there, so nothing reaches adb unless it has the
# exact shape of a package name or a component name.

PKG_RE='^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+$'
COMPONENT_RE='^[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+/[A-Za-z0-9_.$]+$'

# Package names in a list file, one per line: BOM and CRLF from Windows editors stripped, comments
# and blank lines dropped. Shape is not checked here; pass the result through require_pkgs.
read_list() {
  LC_ALL=C awk 'NR==1{sub(/^\357\273\277/,"")} {sub(/\r$/,""); sub(/#.*/,""); if ($1!="") print $1}' "$1"
}

# Exit unless every line of $2 is a bare package name. $1 names the source in the message.
require_pkgs() {
  local bad
  bad=$(printf '%s\n' "$2" | sed '/^$/d' | grep -vE "$PKG_RE" || true)
  if [ -n "$bad" ]; then
    { echo "refusing $1, not a package name:"; printf '%s\n' "$bad" | sed 's/^/  /'; } >&2
    exit 1
  fi
}

is_component() { printf '%s' "$1" | grep -qE "$COMPONENT_RE"; }
