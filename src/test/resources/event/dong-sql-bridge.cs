using System;
using System.IO;
using System.IO.Pipes;
using System.Net;
using System.Net.Sockets;
using System.Threading.Tasks;
public static class DongSqlBridge {
 public static async Task Run() {
  var listener=new TcpListener(IPAddress.Loopback,11433); listener.Start();
  Console.WriteLine("Local SQL test bridge listening on loopback:11433");
  while(true) { var tcp=await listener.AcceptTcpClientAsync(); _=Relay(tcp); }
 }
 private static async Task Relay(TcpClient tcp) {
  using(tcp) using(var pipe=new NamedPipeClientStream(".", "MSSQL$SQLEXPRESS\\sql\\query", PipeDirection.InOut, PipeOptions.Asynchronous)) {
   try { await pipe.ConnectAsync(5000); using(var network=tcp.GetStream()) {
    var a=network.CopyToAsync(pipe); var b=pipe.CopyToAsync(network); await Task.WhenAny(a,b);
   }} catch(Exception e) { Console.WriteLine("Relay closed: "+e.GetType().Name); }
  }
 }
}
