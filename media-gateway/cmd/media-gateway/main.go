package main

import (
	"log"
	"media-gateway/internal/app"
	"media-gateway/internal/config"
)

func main() {
	if err := app.Run(config.Load()); err != nil {
		log.Fatal(err)
	}
}
