import util


def get_big_detour_test():
    return util.Location(lat=49.931488,lon=9.570422), util.Location(lat=49.928190,lon=9.574844)

def get_talsperre_ratscher():
    return util.Location(lat=50.484644, lon=10.777801), util.Location(lat=50.494334, lon=10.797822)

def get_alte_mainbruecke():
    # mainly checks if bridges only allowed for pedestrians are considered!
    return util.Location(lat=49.793848, lon=9.918994), util.Location(lat=49.793056, lon=9.936595)

def additional_edge_check():
    # edge case that checks if the additional/new edge is inserted
    return util.Location(lat=50.451058,lon=10.281904), util.Location(lat=50.428449,lon=10.302903)

def get_large_distance_test():
    # Aschaffenburg -> Wuerzburg
    return util.Location(lat=49.985769, lon=9.121876), util.Location(lat=49.781181,lon=9.973124)

def get_lake_test():
    return util.Location(lat=50.160122, lon=10.363829), util.Location(lat=50.122980, lon=10.384127)

def get_do_not_cross_test():
    return util.Location(lat=49.959984, lon=9.593918), util.Location(lat=49.892227, lon=9.591732)

def get_no_water_area_test():
    return util.Location(lat=50.011555, lon=9.652666), util.Location(lat=49.992718, lon=9.681179)


