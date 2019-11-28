#!/usr/bin/env bash
port_used_kc="$(lsof -t -i :9990 -s TCP:LISTEN)"
echo "coucou ${port_used_kc}"
kill ${port_used_kc}

port_used_kc="$(lsof -t -i :8080 -s TCP:LISTEN)"
echo "coucou ${port_used_kc}"
kill ${port_used_kc}
