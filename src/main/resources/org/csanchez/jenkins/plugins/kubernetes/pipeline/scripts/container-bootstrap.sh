#!/bin/sh
# Run by sh in the container to look for commands to run, which will be written the agent container to the workspace volume.
# Must use only portable POSIX sh features, as the container may be running any distribution, even BusyBox.
set -eu
dir="$JENKINS_CONTAINER_WORK/$JENKINS_CONTAINER_NAME"
# TODO parallelize
while true
do
  for procdir in "$dir"/*
  do
    if test -f "$procdir/script.sh" -a \! -f "$procdir/seen"
    then
      echo "Handling $procdir"
      touch "$procdir/seen"
      `cat "$procdir/shell.txt"` "$procdir/script.sh" <&- >"$procdir/out.txt" 2>"$procdir/err.txt"
      r=$?
      echo $r >"$procdir/status.txt.tmp"
      mv "$procdir/status.txt.tmp" "$procdir/status.txt"
      echo "Completed $procdir with status $r"
    fi
  done
  sleep 1
done
