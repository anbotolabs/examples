import requests
import json
import hmac
import hashlib
from enum import Enum
import base64
import time
import uuid


class ApiErrorCode(Enum):
    OTHER = "OTHER"
    QUANTITY_EXCEED = "QUANTITY_EXCEED"
    AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR"
    INSUFFICIENT_FUNDS = "INSUFFICIENT_FUNDS"
    RATE_LIMIT_EXCEEDED = "RATE_LIMIT_EXCEEDED"
    DDOS_PROTECTION = "DDOS_PROTECTION"
    EXCHANGE_NOT_AVAILABLE = "EXCHANGE_NOT_AVAILABLE"
    NETWORK_ERROR = "NETWORK_ERROR"
    INVALID_ORDER = "INVALID_ORDER"
    EXCHANGE_ERROR = "EXCHANGE_ERROR"
    EMS_INSTANCES_DOWN = "EMS_INSTANCES_DOWN"
    SLIPPAGE_EXCEEDED = "SLIPPAGE_EXCEEDED"
    EXPIRY_REACHED = "EXPIRY_REACHED"
    MAX_FEE_PER_GAS_IS_TOO_LOW = "MAX_FEE_PER_GAS_IS_TOO_LOW"
    PARENT_ORDER_WAS_TERMINATED = "PARENT_ORDER_WAS_TERMINATED"
    MAX_PRIORITY_FEE_PER_GAS_IS_TOO_LOW = "MAX_PRIORITY_FEE_PER_GAS_IS_TOO_LOW"
    INVALID_SIGNATURE = "INVALID_SIGNATURE"
    INVALID_API_KEY = "INVALID_API_KEY"
    INVALID_TIMESTAMP = "INVALID_TIMESTAMP"
    SYSTEM_ERROR = "SYSTEM_ERROR"
    INVALID_REQUEST = "INVALID_REQUEST"

class AssetCategory(Enum):
    SPOT = "SPOT"
    FUTURE = "FUTURE"
    OPTION = "OPTION"

class Exchange(Enum):
    BINANCE = "BINANCE"
    HUOBI = "HUOBI"
    GATEIO = "GATEIO"
    KRAKEN = "KRAKEN"
    KUCOIN = "KUCOIN"
    OKX = "OKX"
    BYBIT = "BYBIT"
    BITGET = "BITGET"

class ExecutionStrategy(Enum):
    TWAP = "TWAP"
    VWAP = "VWAP"
    ICEBERG = "ICEBERG"
    MARKET = "MARKET"
    LIMIT = "LIMIT"
    STOP_LOSS = "STOP_LOSS"

class OrderStatusEnum(Enum):
    PENDING_NEW = "PENDING_NEW"
    ACCEPTED = "ACCEPTED"
    REJECTED = "REJECTED"
    PARTIALLY_FILLED = "PARTIALLY_FILLED"
    FILLED = "FILLED"
    PENDING_CANCEL = "PENDING_CANCEL"
    CANCELLED = "CANCELLED"
    PENDING_PAUSE = "PENDING_PAUSE"
    PAUSED = "PAUSED"
    PENDING_UNPAUSE = "PENDING_UNPAUSE"
    EXPIRED = "EXPIRED"
    CANCEL_REJECTED = "CANCEL_REJECTED"

class Side(Enum):
    BUY = "BUY"
    SELL = "SELL"
    SHORT_SELL = "SHORT_SELL"



def get_current_millis():
    # Get current time in seconds since the epoch
    current_time_seconds = time.time()

    # Convert seconds to milliseconds and return as a string
    return str(int(current_time_seconds * 1000))



class AnbotoTradingAPI:
    def __init__(self, base_url, api_key, secret_key):
        self.base_url = base_url
        self.api_key = api_key
        self.secret_key = base64.b64decode(secret_key)  # Decode api_secret
        
    def gen_signature(self, timestamp, querystring, body):
        recv_window = "5000"

        sb = timestamp + self.api_key + recv_window + querystring + body
        hmac_sha256 = hmac.new(self.secret_key, sb.encode(), hashlib.sha256)
        sign = base64.b64encode(hmac_sha256.digest()).decode()
        return sign

    def _get_auth_headers(self, params=None, data=None):
        timestamp = get_current_millis()

        recv_window = 5000
        if params:
            query_string = '&'.join([f"{key}={value}" for key, value in sorted(params.items())])
        else:
            query_string = ''
        if data:
            json_body_string = json.dumps(data, separators=(',', ':'))
        else:
            json_body_string = ''

        signature = self.gen_signature(timestamp, query_string, json_body_string)
        headers = {
            'X-API-KEY': self.api_key,
            'X-SIGN': signature,
            'X-TIMESTAMP': str(timestamp),
            'X-RECV-WINDOW': str(recv_window),
        }
        return headers
    
    @staticmethod
    def get_response_content(resp):
        try:
            return resp.json()
        except:
            print(resp.status_code, resp.content)
            return resp

    def get_orders(self, order_ids=None, client_order_ids=None):
        url = f"{self.base_url}/api/v2/trading/order/byId"
        params = {}
        if order_ids:
            params['orderIds'] = ','.join(map(str, order_ids))
        if client_order_ids:
            params['clientOrderIds'] = ','.join(map(str, client_order_ids))
        headers = self._get_auth_headers(params=params)
        response = requests.get(url, params=params, headers=headers)
        return self.get_response_content(response)

    def cancel_order(self, order_data):
        url = f"{self.base_url}/api/v2/trading/order/cancel"
        headers = self._get_auth_headers(data=order_data)
        response = requests.post(url, json=order_data, headers=headers)
        return self.get_response_content(response)

    def cancel_many_orders(self, order_data):
        url = f"{self.base_url}/api/v2/trading/order/cancelMany"
        headers = self._get_auth_headers(data=order_data)
        response = requests.post(url, json=order_data, headers=headers)
        return self.get_response_content(response)

    def create_order(self, order_data):
        url = f"{self.base_url}/api/v2/trading/order/create"
        headers = self._get_auth_headers(data=order_data)
        response = requests.post(url, json=order_data, headers=headers)
        return self.get_response_content(response)
    
    def create_many_orders(self, orders, all_or_none=True):
        url = f"{self.base_url}/api/v2/trading/order/createMany"
        order_data = {"orders": orders, "allOrNone": all_or_none}
        headers = self._get_auth_headers(data=order_data)
        response = requests.post(url, json=order_data, headers=headers)
        return self.get_response_content(response)

    def find_orders(self, start_ms=None, end_ms=None, limit=None):
        url = f"{self.base_url}/api/v2/trading/order/find"
        params = {}
        if start_ms:
            params['startMs'] = start_ms
        if end_ms:
            params['endMs'] = end_ms
        if limit:
            params['limit'] = limit
        headers = self._get_auth_headers(params=params)
        response = requests.get(url, params=params, headers=headers)
        return self.get_response_content(response)

    def get_open_orders(self):
        url = f"{self.base_url}/api/v2/trading/order/open"
        headers = self._get_auth_headers()
        response = requests.get(url, headers=headers)
        return self.get_response_content(response)
    
    def format_create_order_data(self, client_order_id, exchange, symbol, asset_category, side, quantity, strategy, limit_price=None, start_time=None, end_time=None, clip_size_type=None, clip_size_val=None, params=None):
        order_data = {
            "client_order_id": client_order_id,
            "exchange": exchange,
            "symbol": symbol,
            "asset_category": asset_category,
            "side": side,
            "quantity": quantity,
            "strategy": strategy,
        }
        if limit_price is not None:
            order_data["limitPrice"] = limit_price
        if start_time is not None:
            order_data["startTime"] = start_time
        if end_time is not None:
            order_data["endTime"] = end_time
        if clip_size_type is not None:
            order_data["clipSizeType"] = clip_size_type
        if clip_size_val is not None:
            order_data["clipSizeVal"] = clip_size_val
        if params is not None:
            order_data["params"] = params
        return order_data

    def format_cancel_order_data(self, order_id=None, client_order_id=None):
        if order_id is not None:
            return {"orderId": order_id}
        elif client_order_id is not None:
            return {"clientOrderId": client_order_id}
        else:
            raise ValueError("Either order_id or client_order_id must be provided.")

    def format_cancel_many_orders_data(self, order_ids=None, client_order_ids=None):
        if order_ids is not None:
            return {"orderIds": order_ids}
        elif client_order_ids is not None:
            return {"clientOrderIds": client_order_ids}
        else:
            raise ValueError("Either order_ids or client_order_ids must be provided.")


def main():
    # Example usage:
    base_url = "https://api.testnet.anboto.xyz"
    api_key = "YOUR_API_KEY"
    secret_key = "YOUR_SECRET_KEY"

    trading_api = AnbotoTradingAPI(base_url, api_key, secret_key)


    id1, id2 = str(uuid.uuid4()), str(uuid.uuid4())
    order1 = trading_api.format_create_order_data(
        client_order_id=id1, 
        exchange="binance", 
        symbol="ETH/USDT",
        asset_category=AssetCategory.SPOT.value,
        side=Side.BUY.value,
        quantity=1,
        strategy=ExecutionStrategy.TWAP.value,
        params={"duration_seconds": 300}
    )
    order2 = trading_api.format_create_order_data(
        client_order_id=id2, 
        exchange="binance", 
        symbol="BTC/USDT",
        asset_category=AssetCategory.SPOT.value,
        side=Side.BUY.value,
        quantity=0.1,
        strategy=ExecutionStrategy.TWAP.value,
        params={"duration_seconds": 300}
    )
    print("# Create many orders")
    print([order1, order2])
    orders = trading_api.create_many_orders(orders=[order1, order2])
    print(orders)

    time.sleep(1)
    # Example: Get orders
    orders = trading_api.get_orders(client_order_ids=[id1, id2])
    print("# Get orders")
    print(orders)

    orders = trading_api.get_open_orders()
    print("# Get open orders")
    print(orders)

    time.sleep(1)
    # Example: Cancel order
    cancel_orders = {"orders": [{"client_order_id": _id} for _id in [id1, id2]]}
    cancel_response = trading_api.cancel_many_orders(cancel_orders)
    print("# Cancel many orders")
    print(cancel_response)

if __name__ == "__main__":
    main()
