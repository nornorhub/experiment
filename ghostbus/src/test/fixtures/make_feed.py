"""Regenerates src/test/resources/feed.pb with Google's official GTFS-realtime bindings.

    pip install gtfs-realtime-bindings
    python make_feed.py ../resources/feed.pb
"""
import sys

from google.transit import gtfs_realtime_pb2 as rt

NOW = 1_700_000_000

feed = rt.FeedMessage()
feed.header.gtfs_realtime_version = "2.0"
feed.header.timestamp = NOW


def trip_update(trip_id, route_id, stop_id, time=None, delay=None, departure_only=False,
                vehicle=None, canceled=False, skipped=False):
    e = feed.entity.add(id="tu-" + trip_id)
    tu = e.trip_update
    tu.trip.trip_id = trip_id
    tu.trip.route_id = route_id
    if canceled:
        tu.trip.schedule_relationship = rt.TripDescriptor.CANCELED
    if vehicle:
        tu.vehicle.id = vehicle
    stu = tu.stop_time_update.add(stop_sequence=7, stop_id=stop_id)
    if skipped:
        stu.schedule_relationship = rt.TripUpdate.StopTimeUpdate.SKIPPED
    event = stu.departure if departure_only else stu.arrival
    if time is not None:
        event.time = time
    if delay is not None:
        event.delay = delay


def vehicle(vehicle_id, lat, lon, timestamp, trip_id=None, route_id=None):
    e = feed.entity.add(id="vp-" + vehicle_id)
    vp = e.vehicle
    if trip_id:
        vp.trip.trip_id = trip_id
    if route_id:
        vp.trip.route_id = route_id
    vp.vehicle.id = vehicle_id
    vp.vehicle.label = "Bus " + vehicle_id
    vp.position.latitude = lat
    vp.position.longitude = lon
    vp.position.bearing = 90
    vp.timestamp = timestamp


trip_update("T1", "62", "S1", time=NOW + 300, delay=120, vehicle="V1")
trip_update("T2", "62", "S1", time=NOW + 600)
trip_update("T3", "13", "S1", time=NOW + 900, departure_only=True)
trip_update("T4", "62", "S1", time=NOW + 400, canceled=True)
trip_update("T5", "62", "S2", time=NOW + 100)
trip_update("T6", "62", "S1", time=NOW + 200, skipped=True)
trip_update("T7", "13", "S1", time=NOW + 120, delay=-60, vehicle="V7")
trip_update("T8", "13", "S1", time=NOW + 5 * 3600)

vehicle("V1", 40.7357, -74.1724, NOW - 20, trip_id="T1", route_id="62")
vehicle("V3", 40.70, -74.20, NOW - 600, trip_id="T3", route_id="13")
vehicle("V7", 40.7300, -74.1700, NOW - 5)

alert = feed.entity.add(id="alert-1").alert
alert.active_period.add(start=NOW - 3600, end=NOW + 3600)
alert.informed_entity.add(route_id="62")
alert.informed_entity.add(stop_id="S1")
alert.header_text.translation.add(text="Detour on Broad St", language="es-x")
alert.header_text.translation.add(text="Detour on Broad St", language="en")
alert.description_text.translation.add(text="Buses use Main St ✓")

with open(sys.argv[1], "wb") as f:
    f.write(feed.SerializeToString())
