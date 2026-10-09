"""Utilitaires géographiques : conversion Lambert 93 -> WGS84 et distances.

Les stations de l'API Poisson diffusent leurs coordonnées en Lambert 93
(projection officielle française), l'API Hydrometrie travaille en WGS84.
Formules conformes de la projection (IGN, RGF93).
"""

import math

# Ellipsoide GRS80
A = 6_378_137.0
F_INV = 298.257_222_101
E = math.sqrt(2 / F_INV - 1 / F_INV ** 2)

# Parametres Lambert 93
LON0 = math.radians(3.0)
LAT0 = math.radians(46.5)
PHI1 = math.radians(44.0)
PHI2 = math.radians(49.0)
XS, YS = 700_000.0, 6_600_000.0


def _iso_lat(lat: float) -> float:
    """Latitude isometrique."""
    s = E * math.sin(lat)
    return math.log(math.tan(math.pi / 4 + lat / 2)
                    * ((1 - s) / (1 + s)) ** (E / 2))


def _constantes() -> tuple[float, float]:
    l1, l2 = _iso_lat(PHI1), _iso_lat(PHI2)
    n = math.log(
        (math.cos(PHI1) / math.cos(PHI2))
        * math.sqrt((1 - E ** 2 * math.sin(PHI2) ** 2)
                    / (1 - E ** 2 * math.sin(PHI1) ** 2))
    ) / (l2 - l1)
    c = (A * math.cos(PHI1) * math.exp(n * l1) / n
         * math.sqrt(1 - E ** 2 * math.sin(PHI1) ** 2) ** -1)
    return n, c


_N, _C = _constantes()
_R0 = _C * math.exp(-_N * _iso_lat(LAT0))  # rayon au parallele origine


def lambert93_vers_wgs84(x: float, y: float) -> tuple[float, float]:
    """Conversion Lambert 93 -> (latitude, longitude) en degres WGS84.
    Precision ~1 m, valid pour la France metropolitaine."""
    if x is None or y is None:
        return None, None
    dx = x - XS
    dy = _R0 - (y - YS)
    r = math.hypot(dx, dy)
    gamma = math.atan2(dx, dy)
    lon = LON0 + gamma / _N
    l = -math.log(r / _C) / _N
    # iteration de point fixe : latitude depuis la latitude isometrique
    lat = LAT0
    for _ in range(20):
        s = E * math.sin(lat)
        lat_new = 2 * math.atan(math.exp(l) * ((1 + s) / (1 - s)) ** (E / 2)) \
            - math.pi / 2
        if abs(lat_new - lat) < 1e-13:
            lat = lat_new
            break
        lat = lat_new
    return math.degrees(lat), math.degrees(lon)


def distance_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Distance a vol d'oiseau entre deux points GPS (haversine)."""
    if any(v is None for v in (lat1, lon1, lat2, lon2)):
        return float("inf")
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dp = p2 - p1
    dl = math.radians(lon2 - lon1)
    a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * 6371.009 * math.asin(math.sqrt(a))


def bbox_autour(lat: float, lon: float, rayon_km: float) -> str:
    """Rectangle englobant autour d'un point, format API Hydrometrie :
    lon_min,lat_min,lon_max,lat_max."""
    d_lat = rayon_km / 111.32
    d_lon = rayon_km / (111.32 * max(math.cos(math.radians(lat)), 0.2))
    return f"{lon - d_lon},{lat - d_lat},{lon + d_lon},{lat + d_lat}"
