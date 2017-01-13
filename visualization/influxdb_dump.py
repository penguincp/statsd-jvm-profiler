#!/usr/bin/env python
from optparse import OptionParser
from influxdb import InfluxDBClient
import sys

class InfluxDBDump:
    def __init__(self, host, port, username, password, database, prefix, begin, end, tagmapping):
        self.host = host
        self.port = port
        self.username = username
        self.password = password
        self.database = database
        self.prefix = prefix
        # self.tag_mapping = tag_mapping
        self.client = InfluxDBClient(self.host, self.port, self.username, self.password, self.database)
        self.tag_mapping = self._construct_tag_mapping(prefix, tagmapping)
        self.prefix=prefix
        self.begin=begin
        self.end=end

    def run(self):
        # f = open('c:/devops/explore/statsd-jvm/visualization/influxdb-dashboard/public/scripts/flame','w')
        # clauses = ["%s ='%s'" % (tag, value) for (tag, value) in self.mapped_tags.items()]
        measurement_name=self.prefix+"_cpu_trace"
        query = 'select type, value from /'+measurement_name+'/' #where %s' % " and ".join(clauses)
        whereExists=False
        if self.begin:
            query= query + " where time>='"+self.begin+"'"
            whereExists=True
        if self.end:
            if whereExists:
                query= query + " and time<'"+self.end+"'"
            else:
                query = query + " where time<'"+self.end+"'"
                whereExists=True

        if len(self.tag_mapping)>0:
            clauses = ["%s ='%s'" % (tag, value) for (tag, value) in self.tag_mapping.items()]
            if whereExists:
                query=query+ ' and %s' % " and ".join(clauses)
            else:
                query= query + ' where %s' % " and ".join(clauses)

        ret=self.client.query(query)
        if len(ret)==0:
            raise Exception("no data found!")

        metrics = ret.raw["series"][0]["values"]
        map={}
        for metric in metrics:
            name=metric[1] #the first column is timestamp
            if name in map:
                map[name]=map[name]+metric[2]
            else:
                map[name]=metric[2]

        for key in map:
            name = self._format_metric_name(key, measurement_name)
            value = map[key]
            if name != str(value):
                print('%s %d' % (name, value))

    def _format_metric_name(self, name, prefix):
        tokens = name.replace(prefix, '').split('|')
        reverse = reversed(tokens)
        line_numbers = [':'.join(r.rsplit('-', 1)) for r in reverse]
        return ';'.join(line_numbers).replace('-', '.')

    def _construct_tag_mapping(self, prefix, tag_mapping):
        mapped_tags = {}
        if tag_mapping:
            tags = tag_mapping.split(',')
            for tag in tags:
                keyAndValue=tag.split('=')
                mapped_tags[keyAndValue[0]] = keyAndValue[1]

        return mapped_tags


def get_arg_parser():
    parser = OptionParser()
    parser.add_option('-o', '--host', dest='host', help='Hostname of InfluxDB server', metavar='HOST')
    parser.add_option('-r', '--port', dest='port', help='Port for InfluxDB HTTP API (defaults to 8086)', metavar='PORT')
    parser.add_option('-u', '--username', dest='username', help='Username with which to connect to InfluxDB', metavar='USER')
    parser.add_option('-p', '--password', dest='password', help='Password with which to connect to InfluxDB', metavar='PASSWORD')
    parser.add_option('-d', '--database', dest='database', help='InfluxDB database which contains profiler data', metavar='DB')
    parser.add_option('-e', '--prefix', dest='prefix', help='Metric prefix', metavar='PREFIX')
    parser.add_option('-b', '--begin', dest='begin', help='Data points equal or after this time will be fetched, format is YYYY-MM-DDTHH:MM:SSZ', metavar='PREFIX')
    parser.add_option('-n', '--end', dest='end', help='Data points before this time will be fetched, format is YYYY-MM-DDTHH:MM:SSZ', metavar='PREFIX')
    parser.add_option('-t', '--tagmapping', dest='tagmapping', help='Tag mapping for metric prefix', metavar='MAPPING')

    return parser

if __name__ == '__main__':
    parser = get_arg_parser()
    args, _ = parser.parse_args()
    if not(args.host and args.username and args.password and args.database and args.prefix):
        parser.print_help()
        sys.exit(255)
    port = args.port or 8086
    # tag_mapping = args.mapping or None
    dumper = InfluxDBDump(args.host, port, args.username, args.password, args.database, args.prefix, args.begin, args.end, args.tagmapping)
    dumper.run()

