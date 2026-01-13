

## Items im Server setzten

1. Items mit Position anlegen (bereits vorhanden, verbessert)
   - /item add <x> <y> <z> <displayName> <itemType> [texturePath]
   - Erkennt automatisch ob Koordinaten angegeben sind
2. Items ohne Position anlegen (neu)
   - /item add <displayName> <itemType> [texturePath]
   - Legt Item ohne Position an (nur in Registry, nicht in der Welt)
3. Items auf Position setzen (neu)
   - /item place <itemId> <x> <y> <z>
   - Setzt ein Item ohne Position auf eine Position
   - Prüft ob Item bereits Position hat
   - Prüft ob Zielposition bereits belegt ist
   - Sendet Update an Clients
4. Items von Position entfernen (bereits vorhanden)
   - /item remove <x> <y> <z>
   - Sendet __deleted__ Marker an Clients (bereits implementiert in Zeile 193)



doSend('world','status', 666 )

doSend('world','status', 0 )

doSetShortcut('click0', 'use', {"itemId": "item_1763653693310_uv2m2pu", "wait": 100})

doSetShortcut('click0', 'use', {"command":"notification", commandArgs: [0,"Title","Message","textures/magic/blue_crystal.png"]})

doSend('world','season', 0 ) // NONE
doSend('world','season', 1 ) // WINTER
doSend('world','season', 3 ) // SUMMER

doSend('world','seasonProgress', 0.5 )


## Moon

doMoonTexture(0,'textures/moon/moon1.png');
doMoonEnable(0,'true');
doMoonSize(0, 70);
doMoonPosition(0, 180);
doMoonElevation(0, 60);
doMoonDistance(0, 450);
doMoonPhase(0, 1.0);

## Clouds

# Wolke im Norden, 200 Blöcke entfernt, Höhe 180
doCloudAdd("cloud-north",0, -200, 180, 80, 50, "textures/clouds/cloud1.png", 3, 0, 0);

# Wolke im Osten, 150 Blöcke entfernt, Höhe 160
cloudAdd "cloud-east" 150 0 160 60 40 "textures/clouds/cloud2.png" 5 90 1

# Wolke direkt über der Kamera
cloudAdd "cloud-above" 0 0 200 100 60 "textures/clouds/cloud3.png" 0 0 2

doCloudAdd("cloud-north6.",0, -200, 180, 280, 250, "textures/clouds/cloud6.png", 3, 0, 0);

## More

doSunSize(150)

doSunLensFlareEnable('false')
doAmbientLightIntensity(0)
doSunLightIntensity(.5)

doSkyBoxSize(450)
doSkyBoxTexture('textures/skybox/space')
doSkyBoxEnable('true')
doSunLightDiffuse(1,0,0)
doSunLightSpecular(1,0,0)
doSunLightDirection(-1,1,1)


doScrawlStart({
"root": {
"kind": "Play",
"effectId": "positionFlash",
"ctx": {
"position": {"x": 1, "y": 70, "z": 18},
"duration": 5,
"color": "#ffffff",
"height": 30,
"textureFrames": [
"textures/effects/blitz1.png"
],
"frameRate": 20,
"light": true
}
}
})


// Leichter Schnee
doPrecipitationStart(100, 1.0, 1.0, 1.0, 0.6, 5, 3)

// Mittlerer Schnee
doPrecipitationStart(300, 1.0, 1.0, 1.0, 0.6, 5, 3)

// Schneesturm!
doPrecipitationStart(1000, 1.0, 1.0, 1.0, 0.6, 5, 3)


// Regen (zweites Mal - sollte jetzt funktionieren!)
doPrecipitationStart(530, 0.4, 0.4, 0.6, 0.3, 25, 15)


doLightning(20, true)

doSetStackModifier('sunPosition','default',270, 0.1, 50);

doCloudsAnimationStart('myJob', 10, 0.5, -100, 100, -100, 100, 80, 120, 20, 40, 10, 20, 2.0, 90, 'textures/clouds/cloud1.png,textures/clouds/cloud2.png');

doSkyBoxStart({r:0.5, g:0.7, b:1},45, 450, 'textures/skybox/space')


doNotification(0,"aaa","bbb", "textures/magic/blue_crystal.png")

doSplashscreen('screens/blizzardskull.png','audio/ambiente/blackmoor_tides.mp3')

doSend('teamData','RedTeam','Player1','Player2','Player3')
doSend('teamStatus','team_ teamData','player_0',50,1);    # Player1 health=50, alive - not working yet


doSetShortcut('click0', 'use', {"command":"notification", commandArgs: [0,"Title","Message {{x}} {{y}} {{z}}","textures/magic/blue_crystal.png"], iconPath:"textures/magic/blue_crystal.png" })
doSend('control.EditBlockTrigger','[x,y,z]')

doModelselector('enable','#ff0000',true,true,8,74,8,'#00ff00')
doModelselector('move',1,0,0)
doModelselector('disable')

# Camera Light

doSunLightIntensity(0)
doAmbientLightIntensity(0)
doCameraLightRange(20)
doCameraLightIntensity(2)
doCameraLightEnable(true)

