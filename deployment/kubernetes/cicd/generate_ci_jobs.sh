#!/bin/bash -xe

# Purpose: 
#   a script to generate aerie's image retrieval and pushing logic.
#   it may be possible to make this more generic

# please note that aerie-hasura was manually changed to aerie-hasura:develop
images=(
    ghcr.io/nasa-ammos/aerie-ui:develop
    ghcr.io/nasa-ammos/aerie-hasura:develop
    ghcr.io/nasa-ammos/aerie-gateway:develop
    ghcr.io/nasa-ammos/aerie-merlin:develop
    ghcr.io/nasa-ammos/aerie-merlin-worker:develop
    ghcr.io/nasa-ammos/aerie-scheduler:develop
    ghcr.io/nasa-ammos/aerie-scheduler-worker:develop
    ghcr.io/nasa-ammos/aerie-sequencing:develop
    ghcr.io/nasa-ammos/aerie-postgres:develop
)

mkdir -p .gitlab/Jobs/

for image in "${images[@]}"
do
    image_bn=$(basename $image)
    image_name=$(echo $image_bn | cut -d: -f1)
    tag=$(echo $image_bn | cut -d: -f2)

    mkdir -p ./${image_name}
    echo FROM $image > ./${image_name}/Dockerfile

touch .gitlab/Jobs/build-${image_name}.ci.yml

cat << EOF > .gitlab/Jobs/build-${image_name}.ci.yml
include:
  - local: ".gitlab/Jobs/_build-app.ci.yml"

variables:
  CS_DOCKERFILE_PATH: ${image_name}/Dockerfile
  CI_APPLICATION_REPOSITORY: \$CI_REGISTRY_IMAGE/${image_name}

EOF

    if grep -q "${image_name}" .gitlab-ci.yml
    then

    echo No need to add "${image_name}" to .gitlab-ci.yml since it already exists

    else

# if image_name is NOT found in .gitlab-ci.yml then add it

cat << EOF >> .gitlab-ci.yml

build_${image_name}_image:
  extends: .build_image
  variables:
    IMAGE_NAME: ${image_name}
EOF

    fi

done
