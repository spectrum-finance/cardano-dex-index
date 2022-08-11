docker rm -f markets-api-scala &&
docker rmi $(docker images --filter "dangling=true" -q --no-trunc) &&
docker run \
    -v /home/apps/analytics-api-scala/conf/app.conf:/cardano-analytics-scala/conf/conf.env:ro \
    --restart=always \
    --network="dev" \
    --name=markets-api-scala \
    --publish 8081:8081 \
    -d timooxaaa/markets-api-spectrum:0.0.1