# Meth Receiver Dashboard - author Arjun Kula

Android tablet dashboard for receiver-side methane telemetry.

## Features
- Connects to receiver Pi over Wi-Fi hotspot
- Streams telemetry over WebSocket
- Displays methane, wind speed, wind direction, and system status
- Tablet landscape dashboard UI

## Receiver Pi
Expected WebSocket endpoint:

ws://192.168.4.1:8765

## Current telemetry fields
- timestamp
- methane_ppm
- wind_speed_mps
- wind_direction_deg
- gps_lat
- gps_lon
- radio_rssi
- packet_id

## Notes
Some fields may show `--` until connected sensors are enabled.