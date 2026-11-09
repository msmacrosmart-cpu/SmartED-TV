
# -*- coding: utf-8 -*-
import os
from kodi_six import xbmc
from control import login_interface, painel_interface
from variables import SESSION_FILE

if __name__ == '__main__':
    xbmc.executebuiltin("Dialog.Close(busydialog)")
    if os.path.exists(SESSION_FILE):
        painel_interface()
    else:
        login_interface()