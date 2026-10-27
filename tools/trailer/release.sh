#!/usr/bin/env bash
# Файлы трейлера в черновике релиза «trailer-assets» (черновик виден только владельцам репозитория).
#   release.sh download <имя в релизе> <куда>
#   release.sh upload <файл> <имя в релизе>
set -euo pipefail
repo="${GITHUB_REPOSITORY:-Dobrelya22/zangetsu-mod}"
id="$(gh api "repos/$repo/releases?per_page=100" --jq '.[] | select(.name == "trailer-assets") | .id' | head -1)"
if [ -z "$id" ]; then
  echo "Нет черновика релиза trailer-assets" >&2
  exit 1
fi
case "$1" in
  download)
    asset="$(gh api "repos/$repo/releases/$id/assets" --jq ".[] | select(.name == \"$2\") | .id")"
    gh api -H "Accept: application/octet-stream" "repos/$repo/releases/assets/$asset" > "$3"
    ;;
  upload)
    old="$(gh api "repos/$repo/releases/$id/assets" --jq ".[] | select(.name == \"$3\") | .id")"
    if [ -n "$old" ]; then
      gh api -X DELETE "repos/$repo/releases/assets/$old"
    fi
    curl -sSf -X POST -H "Authorization: Bearer $GH_TOKEN" -H "Content-Type: application/octet-stream" \
      --data-binary @"$2" "https://uploads.github.com/repos/$repo/releases/$id/assets?name=$3" > /dev/null
    echo "Загружено: $3"
    ;;
esac
