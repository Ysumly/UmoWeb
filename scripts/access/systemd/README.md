# UmoWeb access systemd units

`install-access-timer.sh` renders `@INSTALL_ROOT@`, `@REPORT_USER@`, and
`@REPORT_GROUP@` before copying these units into the host systemd directory.
