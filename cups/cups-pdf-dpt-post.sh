#!/bin/bash
echo "sudo -u $2 \"/home/$2/.local/bin/dpt print $1\"" >> /tmp/postprocessing.log
echo `whoami` >> /tmp/postprocessing.log
echo `who` >> /tmp/postprocessing.log
sudo -u $2 /home/$2/.local/bin/dpt print $1 1>> /tmp/postprocessing.log 2>>/tmp/postprocessing.log
